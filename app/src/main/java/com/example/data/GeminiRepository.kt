package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.model.AttachedFile
import com.example.model.ChatMessage
import com.example.model.GroundingSource
import com.example.model.MessageSender
import com.example.util.FileExporter
import com.example.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class SendMessageResult(
    val message: ChatMessage,
    val failoverNotice: String? = null
)

data class StreamUpdate(
    val accumulatedText: String,
    val groundingSources: List<GroundingSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val failoverNotice: String? = null,
    val isDone: Boolean = false,
    val error: String? = null
)

class GeminiRepository(private val context: Context) {
    val apiKeyManager = ApiKeyManager(context)

    private val client = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val MODEL_ID = "gemini-2.0-flash-lite"
    }

    /**
     * Pre-connects / warms up the OkHttpClient connection pool and TLS handshake
     * in the background so that user messages encounter zero handshake latency.
     */
    fun warmupConnection() {
        val keys = apiKeyManager.getValidKeysWithIndices()
        if (keys.isEmpty()) return
        val testKey = keys.first().second
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$testKey")
            .head()
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

    /**
     * Determines whether search grounding is truly necessary for the prompt
     * to prevent running slow 4-second web searches on standard greetings or coding.
     */
    private fun isSearchWarranted(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("haber") || lower.contains("bugün") || lower.contains("hava") ||
                lower.contains("fiyat") || lower.contains("dolar") || lower.contains("euro") ||
                lower.contains("maç") || lower.contains("son dakika") || lower.contains("tarih") ||
                lower.contains("saat") || lower.contains("araştır") || lower.contains("kimdir") ||
                lower.contains("2025") || lower.contains("2026") || lower.contains("en son")
    }

    val memoryManager = MemoryManager(context)
    val taskManager = TaskManager(context)
    val routineContextManager = RoutineContextManager(context)

    fun sendMessageStream(
        userPrompt: String,
        attachedFiles: List<AttachedFile> = emptyList(),
        attachedFile: AttachedFile? = null,
        conversationHistory: List<ChatMessage> = emptyList(),
        persona: com.example.model.AssistantPersona = com.example.model.AssistantPersona.GENERAL,
        isWebSearchEnabled: Boolean = false
    ): Flow<StreamUpdate> = flow {
        val validKeys = apiKeyManager.getValidKeysWithIndices()

        if (validKeys.isEmpty()) {
            emit(
                StreamUpdate(
                    accumulatedText = "Merhaba! Sohbet sistemi hazır.\n\n" +
                            "3 API anahtarı tanımlamak için sağ üstteki 3 nokta menüsünden 'API & Model Ayarları' seçeneğine tıklayabilirsiniz. " +
                            "Model olarak Gemini Flash-Lite Latest (gemini-2.0-flash-lite) kullanılmaktadır.",
                    failoverNotice = null,
                    isDone = true
                )
            )
            return@flow
        }

        val attemptsCount = validKeys.size
        var currentActive = apiKeyManager.getActiveKey() ?: validKeys.first()

        // Only run search tool if web search is enabled AND prompt actually contains time/news/lookup intent
        var useSearchGrounding = isWebSearchEnabled && isSearchWarranted(userPrompt)

        // Automatically detect and extract real web/YouTube/Spotify page contents if prompt contains link(s)
        val detectedUrls = com.example.util.WebPageExtractor.extractUrls(userPrompt)
        var webContextPrefix: String? = null
        if (detectedUrls.isNotEmpty()) {
            val webContents = mutableListOf<com.example.util.ExtractedWebContent>()
            for (u in detectedUrls.take(2)) {
                val extracted = com.example.util.WebPageExtractor.fetchUrlContent(u, client)
                if (extracted != null) {
                    webContents.add(extracted)
                }
            }
            if (webContents.isNotEmpty()) {
                val sb = StringBuilder()
                sb.append("======================================================================\n")
                sb.append("KULLANICININ PAYLAŞTIĞI BAĞLANTI(LAR) VE İNCELENEN CANLI SAYFA İÇERİKLERİ:\n")
                for (w in webContents) {
                    sb.append("\n[BAĞLANTI: ${w.url} (${w.platformType})]\n")
                    if (!w.title.isNullOrBlank()) sb.append("Başlık: ${w.title}\n")
                    if (!w.description.isNullOrBlank()) sb.append("Açıklama: ${w.description}\n")
                    if (!w.mainContent.isNullOrBlank()) {
                        sb.append("İçerik Detayları:\n${w.mainContent}\n")
                    }
                }
                sb.append("======================================================================\n\n")
                webContextPrefix = sb.toString()
            }
        }

        // Use instant in-memory context and memories for lightning-fast responses (Firestore snapshot listeners keep them live in real-time)
        val freshRoutineContext = routineContextManager.routineSummary.value
        val freshMemories = memoryManager.getFormattedMemoriesForSystemPrompt()
        val freshRules = memoryManager.getFormattedRulesForSystemPrompt()

        val allCurrentFiles = if (attachedFiles.isNotEmpty()) attachedFiles else listOfNotNull(attachedFile)

        attemptLoop@ for (attempt in 0 until attemptsCount) {
            val keyIndex = currentActive.first
            val keyValue = currentActive.second

            val accumulated = StringBuilder()
            val accumulatedSources = mutableListOf<GroundingSource>()
            val accumulatedQueries = mutableListOf<String>()

            var continuationAttempt = 0
            val maxContinuations = 2
            var isOverallSuccess = false

            continuationLoop@ while (continuationAttempt <= maxContinuations) {
                val isContinuation = continuationAttempt > 0
                val requestBody = buildRequestBody(
                    userPrompt = userPrompt,
                    attachedFiles = allCurrentFiles,
                    attachedFile = allCurrentFiles.firstOrNull(),
                    conversationHistory = conversationHistory,
                    includeSearchGrounding = if (isContinuation) false else useSearchGrounding,
                    persona = persona,
                    freshRoutineContext = freshRoutineContext,
                    freshMemoriesText = freshMemories,
                    freshRulesText = freshRules,
                    continuationAssistantText = if (isContinuation) accumulated.toString() else null,
                    webContextPrefix = webContextPrefix
                )

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_ID:streamGenerateContent?alt=sse&key=$keyValue"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache")
                    .post(requestBody)
                    .build()

                var lastFinishReason: String? = null

                try {
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        val code = response.code
                        val errBody = response.body?.string().orEmpty()
                        val lowerErr = errBody.lowercase()

                        val is429or503 = code == 429 || code == 503 ||
                                lowerErr.contains("quota") || lowerErr.contains("rate") ||
                                lowerErr.contains("resource_exhausted") || lowerErr.contains("overloaded")

                        if (is429or503) {
                            if (attempt < attemptsCount - 1) {
                                val reason = if (code == 503 || lowerErr.contains("overloaded")) "Sunucu yoğun (503)" else "Kota sınırı (429)"
                                val nextKey = apiKeyManager.switchToNextKey(keyIndex, reason)
                                if (nextKey != null) {
                                    currentActive = nextKey
                                    delay(600)
                                    continue@attemptLoop
                                }
                            }
                            if (useSearchGrounding) {
                                useSearchGrounding = false
                                delay(600)
                                continue@attemptLoop
                            }
                        }

                        val specificMsg = when {
                            code == 503 -> "HTTP 503 (Sunucu Yoğun): Google sunucuları şu anda yoğun. Lütfen birkaç saniye sonra tekrar deneyin."
                            code == 429 -> "HTTP 429 (Kota Sınırı): Gemini dakikalık istek kotası doldu. Birkaç saniye sonra tekrar deneyin veya yedek API anahtarı ekleyin."
                            code == 404 -> "HTTP 404: Model ($MODEL_ID) bulunamadı. Lütfen API anahtarınızı kontrol edin."
                            else -> "HTTP $code: $errBody"
                        }
                        emit(StreamUpdate(accumulatedText = "", error = specificMsg, isDone = true))
                        return@flow
                    }

                    val bodyStream = response.body?.byteStream()
                    if (bodyStream == null) {
                        emit(StreamUpdate(accumulatedText = "", error = "Boş yanıt alındı.", isDone = true))
                        return@flow
                    }

                    isOverallSuccess = true
                    val reader = BufferedReader(InputStreamReader(bodyStream, Charsets.UTF_8))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val l = line?.trim().orEmpty()
                        if (l.startsWith("data:")) {
                            val jsonPart = l.removePrefix("data:").trim()
                            if (jsonPart.isNotEmpty() && jsonPart != "[DONE]") {
                                try {
                                    val chunkJson = JSONObject(jsonPart)
                                    val candidates = chunkJson.optJSONArray("candidates")
                                    val firstCand = candidates?.optJSONObject(0)

                                    val finish = firstCand?.optString("finishReason")
                                    if (!finish.isNullOrBlank()) {
                                        lastFinishReason = finish
                                    }

                                    val content = firstCand?.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    if (parts != null) {
                                        for (i in 0 until parts.length()) {
                                            val part = parts.getJSONObject(i)
                                            if (part.has("text")) {
                                                val isThought = part.optBoolean("thought", false)
                                                if (!isThought) {
                                                    val text = part.getString("text")
                                                    accumulated.append(text)
                                                }
                                            }
                                        }
                                    }

                                    val groundingMeta = firstCand?.optJSONObject("groundingMetadata")
                                    if (groundingMeta != null) {
                                        parseGroundingData(groundingMeta, accumulatedSources, accumulatedQueries)
                                    }

                                    emit(
                                        StreamUpdate(
                                            accumulatedText = accumulated.toString(),
                                            groundingSources = accumulatedSources.toList(),
                                            searchQueries = accumulatedQueries.toList(),
                                            failoverNotice = null,
                                            isDone = false
                                        )
                                    )
                                } catch (_: Exception) {
                                    // Ignore partial chunk parse error
                                }
                            }
                        }
                    }
                    response.close()

                    // If response was cut off due to MAX_TOKENS, seamlessly continue generating the rest!
                    if (lastFinishReason == "MAX_TOKENS" && continuationAttempt < maxContinuations) {
                        continuationAttempt++
                        delay(150)
                        continue@continuationLoop
                    } else {
                        break@continuationLoop
                    }

                } catch (e: Exception) {
                    if (!isOverallSuccess && attempt < attemptsCount - 1) {
                        val nextKey = apiKeyManager.switchToNextKey(keyIndex, e.localizedMessage ?: "Bağlantı hatası")
                        if (nextKey != null) {
                            currentActive = nextKey
                            continue@attemptLoop
                        }
                    }
                    emit(
                        StreamUpdate(
                            accumulatedText = "",
                            error = e.localizedMessage ?: "Bağlantı sırasında bir hata oluştu.",
                            isDone = true
                        )
                    )
                    return@flow
                }
            }

            if (isOverallSuccess) {
                val finalOutput = accumulated.toString()
                emit(
                    StreamUpdate(
                        accumulatedText = finalOutput.ifBlank { "Yanıt alınamadı." },
                        groundingSources = accumulatedSources.toList(),
                        searchQueries = accumulatedQueries.toList(),
                        failoverNotice = null,
                        isDone = true
                    )
                )
                return@flow
            }
        }

        emit(
            StreamUpdate(
                accumulatedText = "",
                error = "Tüm 3 API anahtarının kotası tükendi veya sunucu yanıt vermedi. Lütfen API anahtarlarınızı kontrol edin.",
                isDone = true
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(
        userPrompt: String,
        attachedFiles: List<AttachedFile> = emptyList(),
        attachedFile: AttachedFile? = null,
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<SendMessageResult> = withContext(Dispatchers.IO) {
        val validKeys = apiKeyManager.getValidKeysWithIndices()

        if (validKeys.isEmpty()) {
            return@withContext Result.success(
                SendMessageResult(
                    message = ChatMessage(
                        sender = MessageSender.AI,
                        text = "Merhaba! Sohbet sistemi hazır.\n\n" +
                                "3 API anahtarı tanımlamak için sağ üstteki 3 nokta menüsünden 'API & Model Ayarları' seçeneğine tıklayabilirsiniz."
                    ),
                    failoverNotice = null
                )
            )
        }

        val attemptsCount = validKeys.size
        var currentActive = apiKeyManager.getActiveKey() ?: validKeys.first()
        var useSearchGrounding = isSearchWarranted(userPrompt)

        val detectedUrls = com.example.util.WebPageExtractor.extractUrls(userPrompt)
        var webContextPrefix: String? = null
        if (detectedUrls.isNotEmpty()) {
            val webContents = mutableListOf<com.example.util.ExtractedWebContent>()
            for (u in detectedUrls.take(2)) {
                val extracted = com.example.util.WebPageExtractor.fetchUrlContent(u, client)
                if (extracted != null) webContents.add(extracted)
            }
            if (webContents.isNotEmpty()) {
                val sb = StringBuilder()
                sb.append("======================================================================\n")
                sb.append("KULLANICININ PAYLAŞTIĞI BAĞLANTI(LAR) VE İNCELENEN CANLI SAYFA İÇERİKLERİ:\n")
                for (w in webContents) {
                    sb.append("\n[BAĞLANTI: ${w.url} (${w.platformType})]\n")
                    if (!w.title.isNullOrBlank()) sb.append("Başlık: ${w.title}\n")
                    if (!w.description.isNullOrBlank()) sb.append("Açıklama: ${w.description}\n")
                    if (!w.mainContent.isNullOrBlank()) sb.append("İçerik Detayları:\n${w.mainContent}\n")
                }
                sb.append("======================================================================\n\n")
                webContextPrefix = sb.toString()
            }
        }

        val freshRoutineContext = routineContextManager.routineSummary.value
        val freshMemories = memoryManager.getFormattedMemoriesForSystemPrompt()
        val freshRules = memoryManager.getFormattedRulesForSystemPrompt()

        val allCurrentFiles = if (attachedFiles.isNotEmpty()) attachedFiles else listOfNotNull(attachedFile)

        attemptLoop@ for (attempt in 0 until attemptsCount) {
            val keyIndex = currentActive.first
            val keyValue = currentActive.second

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_ID:generateContent?key=$keyValue"

            try {
                val requestBody = buildRequestBody(
                    userPrompt = userPrompt,
                    attachedFiles = allCurrentFiles,
                    attachedFile = allCurrentFiles.firstOrNull(),
                    conversationHistory = conversationHistory,
                    includeSearchGrounding = useSearchGrounding,
                    freshRoutineContext = freshRoutineContext,
                    freshMemoriesText = freshMemories,
                    freshRulesText = freshRules,
                    webContextPrefix = webContextPrefix
                )
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBodyString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val errorMsg = responseBodyString.lowercase()

                    val is429or503 = response.code == 429 || response.code == 503 ||
                            errorMsg.contains("quota") || errorMsg.contains("rate") ||
                            errorMsg.contains("resource_exhausted") || errorMsg.contains("overloaded")

                    if (is429or503) {
                        if (attempt < attemptsCount - 1) {
                            val reason = if (response.code == 503 || errorMsg.contains("overloaded")) "Sunucu yoğun (503)" else "Kota doldu (429)"
                            val nextKey = apiKeyManager.switchToNextKey(keyIndex, reason)
                            if (nextKey != null) {
                                currentActive = nextKey
                                continue@attemptLoop
                            }
                        }

                        if (useSearchGrounding) {
                            useSearchGrounding = false
                            delay(800)
                            continue
                        }
                    }

                    val errText = when {
                        response.code == 429 -> "HTTP 429 (Kota Sınırı): İstek kotası doldu. Lütfen biraz bekleyin veya yedek anahtar ekleyin."
                        response.code == 404 -> "HTTP 404: Model ($MODEL_ID) bulunamadı."
                        else -> "HTTP ${response.code}: $responseBodyString"
                    }
                    return@withContext Result.failure(Exception(errText))
                } else {
                    val jsonResponse = JSONObject(responseBodyString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                        ?: return@withContext Result.failure(Exception("Model yanıt vermedi."))

                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    var replyText = ""

                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("text")) {
                                    val t = part.getString("text")
                                    replyText += (if (replyText.isEmpty()) "" else "\n") + t
                                }
                            }
                        }

                        val sources = mutableListOf<GroundingSource>()
                        val queries = mutableListOf<String>()
                        val groundingMeta = firstCandidate.optJSONObject("groundingMetadata")
                        if (groundingMeta != null) {
                            parseGroundingData(groundingMeta, sources, queries)
                        }

                        return@withContext Result.success(
                            SendMessageResult(
                                message = ChatMessage(
                                    sender = MessageSender.AI,
                                    text = replyText.ifBlank { "Yanıt alınamadı." },
                                    groundingSources = sources,
                                    searchQueries = queries
                                ),
                                failoverNotice = null
                            )
                        )
                    }
                } catch (e: Exception) {
                    if (attempt < attemptsCount - 1) {
                        val nextKey = apiKeyManager.switchToNextKey(keyIndex, e.localizedMessage ?: "Bağlantı hatası")
                        if (nextKey != null) {
                            currentActive = nextKey
                            continue@attemptLoop
                        }
                    }
                    return@withContext Result.failure(e)
                }
            }
        return@withContext Result.failure(Exception("Tüm 3 API anahtarının kotası tükendi veya sunucu yanıt vermedi."))
    }

    private fun parseGroundingData(
        groundingMeta: JSONObject,
        sources: MutableList<GroundingSource>,
        queries: MutableList<String>
    ) {
        val chunks = groundingMeta.optJSONArray("groundingChunks")
        if (chunks != null) {
            for (c in 0 until chunks.length()) {
                val chunkObj = chunks.optJSONObject(c)
                val webObj = chunkObj?.optJSONObject("web")
                val uri = webObj?.optString("uri").orEmpty().trim()
                val title = webObj?.optString("title").orEmpty().trim()
                if (uri.isNotBlank()) {
                    val exists = sources.any { it.url == uri }
                    if (!exists) {
                        sources.add(
                            GroundingSource(
                                title = if (title.isNotBlank()) title else uri,
                                url = uri
                            )
                        )
                    }
                }
            }
        }

        val webQueries = groundingMeta.optJSONArray("webSearchQueries")
        if (webQueries != null) {
            for (q in 0 until webQueries.length()) {
                val queryStr = webQueries.optString(q).trim()
                if (queryStr.isNotBlank() && !queries.contains(queryStr)) {
                    queries.add(queryStr)
                }
            }
        }
    }

    private fun collectActiveProjectFiles(
        attachedFiles: List<AttachedFile>,
        conversationHistory: List<ChatMessage>
    ): Map<String, String> {
        val filesMap = mutableMapOf<String, String>()

        // 1. Chronologically scan conversation history for user files and AI generated code
        // CRITICAL: Only text files can be added to filesMap! Never attempt to read binary PDF or image as text!
        for (msg in conversationHistory) {
            if (msg.sender == MessageSender.USER) {
                for (f in msg.allAttachedFiles) {
                    if (f.isText && !f.isPdf && !f.isImage) {
                        val text = f.cachedContent ?: com.example.util.FileUtils.readFileContent(context, f)
                        if (!text.isNullOrBlank()) {
                            filesMap[f.name] = if (text.length > 80000) text.take(80000) + "\n[... Dosya kısaltıldı ...]" else text
                        }
                    }
                }
            } else if (msg.sender == MessageSender.AI) {
                for (gen in msg.generatedFiles) {
                    if (!gen.isPdf && gen.content.isNotBlank()) {
                        filesMap[gen.fileName] = gen.content
                    }
                }
                if (msg.text.contains("```file:")) {
                    val extracted = FileExporter.extractGeneratedFiles(msg.text)
                    for (ext in extracted) {
                        if (!ext.isPdf && ext.content.isNotBlank()) {
                            filesMap[ext.fileName] = ext.content
                        }
                    }
                }
            }
        }

        // 2. Add current turn attached files ONLY if they are genuine text
        for (currentFile in attachedFiles) {
            if (currentFile.isText && !currentFile.isPdf && !currentFile.isImage) {
                val currentText = currentFile.cachedContent
                    ?: com.example.util.FileUtils.readFileContent(context, currentFile)
                if (!currentText.isNullOrBlank()) {
                    filesMap[currentFile.name] = if (currentText.length > 80000) currentText.take(80000) + "\n[... Dosya kısaltıldı ...]" else currentText
                }
            }
        }

        return filesMap
    }

    private fun isPromptRelatedToFiles(prompt: String, attachedFile: AttachedFile?): Boolean {
        if (attachedFile != null) return true
        val lower = prompt.lowercase()
        return lower.contains("dosya") || lower.contains("kod") || lower.contains("html") ||
                lower.contains("pdf") || lower.contains("belge") || lower.contains("rapor") ||
                lower.contains("css") || lower.contains("javascript") || lower.contains("python") ||
                lower.contains("script") || lower.contains("düzenle") || lower.contains("güncelle") ||
                lower.contains("ekle") || lower.contains("düzelt") || lower.contains("hata") ||
                lower.contains(".html") || lower.contains(".js") || lower.contains(".py") ||
                lower.contains(".pdf") || lower.contains(".txt") || lower.contains(".json") || lower.contains(".css") ||
                lower.contains("proje") || lower.contains("sayfa") || lower.contains("tasarım")
    }

    private fun buildRequestBody(
        userPrompt: String,
        attachedFiles: List<AttachedFile> = emptyList(),
        attachedFile: AttachedFile? = null,
        conversationHistory: List<ChatMessage>,
        includeSearchGrounding: Boolean = true,
        persona: com.example.model.AssistantPersona = com.example.model.AssistantPersona.GENERAL,
        freshRoutineContext: String? = null,
        freshMemoriesText: String? = null,
        freshRulesText: String? = null,
        continuationAssistantText: String? = null,
        webContextPrefix: String? = null
    ): RequestBody {
        val rootJson = JSONObject()
        val contentsArray = JSONArray()

        val currentTurnFiles = if (attachedFiles.isNotEmpty()) attachedFiles else listOfNotNull(attachedFile)

        val activeFiles = collectActiveProjectFiles(currentTurnFiles, conversationHistory)

        // Optimized token budget (8192 tokens max) and generation tuning for maximum speed, accuracy & coding excellence
        val genConfig = JSONObject().apply {
            put("temperature", 0.6)
            put("topP", 0.95)
            put("topK", 40)
            put("maxOutputTokens", 8192)
        }
        rootJson.put("generationConfig", genConfig)

        // Collect all attached files in this session so sources are never forgotten across conversation turns
        val allSessionFiles: List<AttachedFile> = (conversationHistory.flatMap { it.allAttachedFiles } + currentTurnFiles)
            .distinctBy { it.name }

        // Retain full past conversation history (up to 120 messages) so the entire chat session context is preserved!
        val pastHistory = if (conversationHistory.isNotEmpty() &&
            conversationHistory.last().sender == MessageSender.USER &&
            conversationHistory.last().text == userPrompt) {
            conversationHistory.dropLast(1).takeLast(120)
        } else {
            conversationHistory.takeLast(120)
        }

        for (msg in pastHistory) {
            val contentObj = JSONObject()
            contentObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
            val partsArray = JSONArray()

            val filesSummary = if (msg.allAttachedFiles.isNotEmpty()) {
                val names = msg.allAttachedFiles.joinToString(", ") { "${it.name} (${it.formattedSize})" }
                " [Eklenen Dosyalar: $names]"
            } else ""

            val messageText = (msg.text + filesSummary).trim()

            if (messageText.isNotBlank()) {
                partsArray.put(JSONObject().put("text", messageText))
            }

            if (partsArray.length() > 0) {
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }
        }

        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()

        var promptText = userPrompt

        // MULTIMODAL FILES (PDF & IMAGES) HANDLING:
        // Prioritize:
        // 1. All media files attached in the current turn
        // 2. All media files attached earlier in this session (so user can refer back anytime!)
        val currentMediaFiles = currentTurnFiles.filter { it.isPdf || it.isImage }
        val pastSessionMediaFiles = allSessionFiles.filter { (it.isPdf || it.isImage) && currentTurnFiles.none { cur -> cur.name == it.name } }
        val mediaFilesToFeed = (currentMediaFiles + pastSessionMediaFiles).take(6)

        for (file in mediaFilesToFeed) {
            try {
                val base64Data = file.cachedBase64
                    ?: com.example.util.FileUtils.readFileBase64(context, file)
                    ?: readFileAsBase64(Uri.parse(file.uri))

                if (!base64Data.isNullOrBlank()) {
                    val mime = if (file.isPdf) "application/pdf" else if (file.mimeType.isNotBlank()) file.mimeType else "image/jpeg"
                    val inlineDataObj = JSONObject()
                        .put("mimeType", mime)
                        .put("data", base64Data)

                    val isCurrent = currentTurnFiles.any { it.name == file.name }
                    val label = if (isCurrent) {
                        "[Ekli Dosya: ${file.name}]"
                    } else {
                        "[Sohbette Daha Önce Eklenen Kaynak Dosya: ${file.name}]"
                    }
                    currentParts.put(JSONObject().put("text", label))
                    currentParts.put(JSONObject().put("inlineData", inlineDataObj))
                }
            } catch (t: Throwable) {
                android.util.Log.e("GeminiRepository", "Error feeding base64 for ${file.name}", t)
            }
        }

        // Embed current turn text files if any
        val currentTextFiles = currentTurnFiles.filter { it.isText && !it.isPdf && !it.isImage }
        if (currentTextFiles.isNotEmpty()) {
            val textBuilder = StringBuilder()
            for (f in currentTextFiles) {
                val textContent = activeFiles[f.name] ?: f.cachedContent ?: com.example.util.FileUtils.readFileContent(context, f)
                if (!textContent.isNullOrBlank()) {
                    textBuilder.append("\n[Ekli Dosya: ${f.name}]\nİçerik:\n${textContent.take(40000)}\n---\n")
                }
            }
            if (textBuilder.isNotEmpty()) {
                promptText = "${textBuilder.toString()}\n[Kullanıcı Mesajı]: $promptText"
            }
        }

        if (!webContextPrefix.isNullOrBlank()) {
            promptText = "$webContextPrefix\n$promptText"
        }

        val finalPrompt = promptText.ifBlank {
            if (currentTurnFiles.isNotEmpty()) "Ekli dosyaları incele, analiz et ve açıkla."
            else if (!webContextPrefix.isNullOrBlank()) "İncelenen bağlantı içeriğini analiz et ve açıkla."
            else if (allSessionFiles.isNotEmpty()) "Sohbetteki kaynak dosyayı incele."
            else "Merhaba!"
        }
        currentParts.put(JSONObject().put("text", finalPrompt))

        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)

        if (!continuationAssistantText.isNullOrBlank()) {
            val partialModelTurn = JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", continuationAssistantText)))
            }
            contentsArray.put(partialModelTurn)

            val continueUserTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "Lütfen yanıtına kaldığın tam yerden devam et, başa dönme veya tekrar etme, doğrudan kesilen kelimeden sürdür.")))
            }
            contentsArray.put(continueUserTurn)
        }

        rootJson.put("contents", contentsArray)

        // Only attach Google Search Grounding if enabled
        if (includeSearchGrounding) {
            val toolsArray = JSONArray()
            val searchTool = JSONObject()
            searchTool.put("googleSearch", JSONObject())
            toolsArray.put(searchTool)
            rootJson.put("tools", toolsArray)
        }

        val memoryText = freshMemoriesText ?: memoryManager.getFormattedMemoriesForSystemPrompt()
        val rulesText = freshRulesText ?: memoryManager.getFormattedRulesForSystemPrompt()
        val routineText = freshRoutineContext ?: routineContextManager.routineSummary.value

        val fullSystemPrompt = buildString {
            append("Sen son derece zeki, doğal, samimi, Türkçe dilinde usta, kıdemli baş yazılım mimarı ve internetteki en güncel bilgilere erişebilen çok yönlü bir kişisel yapay zeka asistanısın.\n\n")
            if (rulesText.isNotBlank()) {
                append(rulesText).append("\n\n")
            }
            if (memoryText.isNotBlank()) {
                append(memoryText).append("\n\n")
            }
            if (routineText.isNotBlank()) {
                append(routineText).append("\n\n")
            }

            if (allSessionFiles.isNotEmpty()) {
                append("======================================================================\n")
                append("BU SOHBETTE PAYLAŞILAN TÜM DOSYALAR VE ÇALIŞMA KAYNAKLARI (KALICI HAFIZA):\n")
                append("Kullanıcı bu sohbette aşağıdaki dosyaları yüklemiştir. Tüm bu dosyalar doğrudan hafızandadır ve sohbet boyunca asla unutulmaz:\n\n")
                allSessionFiles.forEachIndexed { index, file ->
                    val typeStr = when {
                        file.isPdf -> "PDF Dokümanı"
                        file.isImage -> "Görsel / Fotoğraf"
                        file.isText -> "Metin / Kod Dosyası"
                        else -> "Belge"
                    }
                    append("${index + 1}. [${file.name}] - Tür: $typeStr, Boyut: ${file.formattedSize}\n")
                }
                append("\nKURAL: Kullanıcı sohbetin herhangi bir anında (kaç mesaj geçmiş olursa olsun) 'ilk pdf'i oku', 'baştaki dosyaya göre anlat', 'yüklediğim dokümandaki soruları çöz' veya 'kaynaklarda ne yazıyor' dediğinde; o dosyaların içeriğini tam ve eksiksiz hatırla.\n")
                append("Asla 'dosyayı tekrar yükleyin' veya 'dosyayı göremiyorum' deme. Sohbet boyunca paylaşılan tüm dosyalar doğrudan erişimindedir ve soruları bu kaynaklara göre cevapla.\n")
                append("======================================================================\n\n")
            }

            if (activeFiles.isNotEmpty()) {
                append("======================================================================\n")
                append("AKTİF PROJE VE ÇALIŞMA DOSYALARI (SOHBET HAFIZASI & EN GÜNCEL KODLAR):\n")
                append("Bu sohbette kullanıcının yüklediği veya senin geliştirdiğin/değiştirdiğin tüm dosyalar tam ve en güncel halleriyle hafızandadır:\n\n")
                for ((fileName, fileContent) in activeFiles) {
                    append("[AKTİF DOSYA: $fileName (Son Güncel Versiyon)]\n")
                    append("```file:$fileName\n")
                    append(fileContent.trim())
                    append("\n```\n\n")
                }
                append("======================================================================\n\n")
            }

            append("""
            İNTERNET BAĞLANTILARI, WEB SAYFALARI, YOUTUBE VE SPOTIFY ANALİZ REHBERİ (ÇOK ÖNEMLİ):
            1. Kullanıcı bir web sitesi, haber, makale, blog, dokümantasyon, YouTube videosu/kanalı veya Spotify bağlantısı paylaştığında; sistem sayfanın gerçek içeriğini, başlığını, meta verilerini ve metnini otomatik olarak çekip senin önüne koyar.
            2. Bu içerik mesajda [KULLANICININ PAYLAŞTIĞI BAĞLANTI(LAR)] başlığı altında tam ve güncel olarak yer alır.
            3. Kullanıcı bağlantıyla ilgili özet, inceleme, açıklama, soru-cevap veya tavsiye istediğinde; bağlantıdaki gerçek verilere sadık kalarak, doğrudan ve ayrıntılı bir analiz yap. Asla 'link açamıyorum' veya 'internete erişemiyorum' deme; sayfa içeriği doğrudan erişimindedir.

            DERS ÇALIŞMA VE KAYNAK KORUMA KURALI (EN YÜKSEK ÖNCELİK):
            1. Kullanıcının bu sohbette paylaştığı tüm ders notları, PDF'ler, kaynak dokümanlar, soru föyleri, tablolar ve özetler bu sohbetin KALICI VE TEMEL DERS MATERYALİDİR.
            2. Kullanıcı sonraki mesajlarında '1. konuyu anlat', 'soruları çöz', 'özet çıkar', 'şu formülü açıkla' veya 'kaynakta ne yazıyor' dediğinde; kaynakları EKSİKSİZ VE MÜKEMMEL BİR ŞEKİLDE HATIRLA.
            3. Asla 'hangi kaynak?', 'kaynak bulunamadı' veya 'tekrar yükleyin' deme. Ekli kaynak doküman doğrudan erişimindedir ve tüm açıklamalarını bu kaynağa sadık kalarak, adım adım ve netçe yap.

            İNTERNET VE GÜNCEL BİLGİ ARAŞTIRMA KURALI (ÇOK ÖNEMLİ):
            1. Kullanıcının sorusu güncel olaylar, tarih, saat, son haberler, hava durumu, piyasa fiyatları, canlı maçlar, yeni teknolojiler, kişiler, olaylar veya araştırma gerektiren herhangi bir konu içeriyorsa; Google Search aracını kullanarak internette kapsamlı, derinlemesine ve en güncel aramaları yap.
            2. Web aramalarından elde ettiğin en doğru ve güncel bilgileri sentezleyerek kullanıcıya net, doğru ve doğrudan aktar.
            3. Kullanıcının sorduğu konu hakkında bilgilerin en son halini sunmaktan çekinme ve kaynakların doğruluğunu gözet.

            11. SINIF, TYT-AYT VE GELİŞMİŞ MATEMATİK REHBERİ (ÇOK ÖNEMLİ):
            1. Kullanıcı matematik, geometri, fizik, kimya, biyoloji veya sınav sorusu sorduğunda ya da sorunun fotoğrafını attığında; soruyu aceleye getirmeden adım adım ve tertemiz bir dille çöz.
            2. ASLA KARMAŞIK, KOD GİBİ ANLAŞILMAZ HAM LATEX YAZMA:
               Matematiksel işlemleri ve formülleri KESİNLİKLE \begin{aligned}, karmaşık parantezler, \left( \right), gereksiz kod blokları veya okunaksız sembol yığını şeklinde yazma!
               İşlemleri ve formülleri doğrudan Unicode karakterleri (², ³, ⁴, ⁿ, ₁, ₂, √, ±, ×, ÷, ≤, ≥, ≠, π, θ, Δ, α, β, →, ∞, vb.) ve sade kesirler ((a + b) / 2) kullanarak, ders tahtasına yazılan tertemiz bir ders notu gibi doğrudan anlaşılır yaz.
            3. Çözüm adımlarını mantık sırasına göre numaralandır veya alt alta sırala:
               Önce sorudaki verilenleri ve isteneni belirle. Hangi kural veya formülün kullanıldığını netçe yaz.
               Her işlem adımını tane tane göster (Örnek: "Adım 1: Diskriminantı bulalım: Δ = b² - 4ac = (-8)² - 4(2)(6) = 16").
            4. Şıklı sorularda doğru cevabın hangi şık olduğunu ve neden o şık olduğunu gerekçesiyle açıkla; soru kökündeki püf noktaları belirt.

            KULLANICI DOSYALARI ÜZERİNDE DÜZENLEME, GELİŞTİRME VE İLERİ DÜZEY KODLAMA (KRİTİK MİMARİ):
            1. DOSYALARI AYNEN VE EKSİKSİZ AKLINDA TUT:
               Kullanıcının daha önce yüklediği veya sohbette geliştirilen tüm dosyalar yukarıdaki [AKTİF DOSYA] bölümünde en güncel haliyle yer almaktadır.
               Kullanıcı "bu dosyada değişiklik yap", "bunu güncelle", "şunu ekle", "kodda şu hatayı düzelt", "tasarımı geliştir" gibi isteklerde bulunduğunda, dosyanın tüm mevcut kodlarını, mimarisini, yapısını ve amacını eksiksiz hatırla. Dosyayı baştan rastgele uydurma.
            2. MEVCUT KODLARI VE MANTIĞI EKSİKSİZ KORU (ASLA BOZMA & SİLME):
               Kullanıcı bir dosya (HTML, CSS, JavaScript, Python, JSON, Kotlin, metin vb.) üzerinde değişiklik, ekleme veya geliştirme istediğinde; dosyanın çalışan mevcut özelliklerini, stillerini, tasarımını, fonksiyonlarını, event listener'larını ve sınıf yapılarını KESİNLİKLE silme veya bozma! Sadece istenen yeni özellikleri mükemmel bir ahenk ve cerrahi bir hassasiyetle mevcut kodlara entegre et.
            3. ASLA YER TUTUCU / KISALTMA / TEMBELLİK KULLANMA (ZERO PLACEHOLDERS):
               Kod yazarken ASLA '// kodun geri kalanı aynı', '<!-- mevcut stiller burada kalacak -->', '/* ... */', '// TODO' gibi yer tutucu veya kısaltmalar KULLANMA. Kullanıcı dosyayı doğrudan indirip çalıştıracağı veya önizleyeceği için dosyanın 1. satırından son satırına kadar %100 eksiksiz, hatasız, tüm kodları dahil edilmiş tam halini ```file:dosya_adi.uzanti bloğu içine koy.
            4. DÜNYA STANDARDI İLERİ DÜZEY KOD MİMARİSİ:
               - Web Sayfaları & HTML5: Modern, ultra responsive (mobil ve masaüstü mükemmel uyumlu), modern tipografi, şık renk paletleri, yumuşak mikro animasyonlar (transition/keyframes), modern CSS flexbox & grid düzenleri, estetik modern kartlar, gölgeler ve interaktif özellikler içersin. Tek parça halinde çalışabilmesi için CSS'i <style>, JavaScript'i <script> içinde eksiksiz barındır.
               - Python & Algoritmalar: PEP-8 standartlarına tam uyumlu, modern tip ipuçları (type hints), kapsamlı hata yakalama (try-except), anlaşılır değişken isimleri ve yüksek performanslı algoritmalar kullan.
               - JavaScript / TypeScript: ES2023+ modern standartlar (async/await, modern API'lar, modüler ve temiz mimari).
               - Kotlin / Android: Temiz Compose ve modern Kotlin standartları.
               - SVG Çizimler: Temiz viewBox, modern renkler ve ölçeklenebilir pürüzsüz vektörler tasarla.
            5. SOHBETTE KISA VE PROFESYONEL ÖZET, KODLAR DOSYADA:
               Sohbet metninde kullanıcının zamanını çalmadan, dosyada ne gibi yenilikler ve geliştirmeler yaptığını 2-3 samimi, net ve teknik açıdan profesyonel maddeyle açıkla. Kodların tamamını ```file:dosya_adi.uzanti bloğunun içine yerleştir.

            DİNAMİK NİYET VE BAĞLAM ADAPTASYONU (ORGANIC INTENT ADAPTATION):
            1. Sabit ve katı tetikleyicilere bağımlı olma. Kullanıcının mesajındaki tonu, bağlamı ve ihtiyacı anlık olarak analiz et:
               - Gündelik sohbetler ve selamlaşmalarda: Son derece doğal, samimi, kısa, sıcak ve insani bir dille yanıt ver.
               - Şimşek hızında pratik bilgi veya kısa soru/cevaplarda: Lafı uzatmadan, doğrudan, net ve en hızlı şekilde çözüm sun.
               - Derin kodlama, geliştirme ve teknik analizlerde: %100 eksiksiz, üretim kalitesinde, modern mimari kurallarına uygun kod blokları ve profesyonel teknik açıklamalar yap.
               - Strateji ve beyin fırtınasında: Çok yönlü, yapıcı ve zengin perspektifler sun.

            KULLANICI HAFIZASI OTOMATİK KAYIT (AUTO-MEMORY EXTRACTION):
            Eğer kullanıcı sohbette senin aklında tutmanı istediği kişisel bir bilgi, hedef, tercih, sınav durumu veya önemli bir kural belirtirse (örneğin 'unutma', 'aklında tut', 'ben şu sınavına hazırlanıyorum', 'şu teknolojiyi kullanıyorum' vb.), yanıtının en sonuna şu formatta ekle:
            [AUTO_MEMORY: Kaydedilecek bilgi]
            Sistem bunu otomatik olarak algılayıp kullanıcının kişisel hafızasına kaydedecektir.

            DOĞAL VE SADE SOHBET:
            1. Varsayılan tarzın sade, akıcı, hızlı ve tıpkı bir insan gibi doğal olmalıdır.
            2. Normal sohbetlerde, selamlaşmalarda (merhaba, selam, nasılsın, naber vb.) ve kısa/genel sorularda KESİNLİKLE başlık (#), kutucuk (>), ara çizgi (---) veya altı çizili kelimeler KULLANMA. Doğrudan, sade ve net bir dille yanıt ver.
            3. BİÇİMLENDİRME ENFLASYONUNDAN KAÇIN: Cümlelerin içine gereksiz yere kalın (**), altı çizili (__) veya semboller sıkıştırma. Metin sade ve göz yormayan bir akışta olmalı.
            4. DOSYA ANALİZİ: Kullanıcı PDF, metin, kod, tablo veya başka bir dosya paylaştığında dosyanın içeriğini ayrıntılı, dikkatli ve doğru şekilde analiz et.

            KOD VE İNDİRİLEBİLİR DOSYA KURALI (MANDATORY & EN ÖNEMLİ KURAL):
            1. ASLA VE ASLA SOHBETTE HAM KOD YAZMA:
               Kullanıcı HTML, web sayfası, Python, JavaScript, CSS, Kotlin, SVG, JSON veya herhangi bir kod, betik veya belge istediğinde; kodları KESİNLİKLE sohbette doğrudan ham kod bloğu olarak verme!
            2. HER ZAMAN İNDİRİLEBİLİR DOSYA OLARAK VER:
               Her zaman ve istisnasız ```file:dosya_adi.uzanti formatında sun:
               - PDF Dokümanı/Ders Notu/Rapor için: ```file:belge_adi.pdf (Örn: ```file:11_sinif_fizik_notlari.pdf)
               - HTML için: ```file:index.html (veya sayfa_adi.html)
               - Python için: ```file:script.py
               - JavaScript için: ```file:script.js
               - CSS için: ```file:style.css
               - SVG çizimi için: ```file:cizim.svg
               - JSON verisi için: ```file:veri.json
               - Metin/not belgesi için: ```file:notlar.txt
               - Diğer dosyalar için: ```file:dosya_adi.uzanti
            3. PDF DOKÜMANI VE DERS NOTU/RAPOR OLUŞTURMA KURALI (KRİTİK & KODSUZ TEMİZ PDF):
               - Kullanıcı PDF, ders notu, özet, soru föyü, çalışma rehberi, rapor, döküman veya belge istediğinde içeriği MUTLAKA ```file:belge_adi.pdf bloğunun içine koy.
               - PDF İÇERİSİNDE ASLA KOD BLOKLARI, HTML TAGLERİ VEYA LATEX KOMUTLARI YAZMA!
               - Başlıkları ve alt başlıkları net ve düzgün metin olarak yaz (Örn: '1. Giriş ve Temel Kavramlar', 'Newton'un Hareket Yasaları').
               - Maddeleri temiz tire (-) veya madde imi ile sırala.
               - Önemli noktaları 'Not: ...' veya 'Püf Noktası: ...' veya 'Formül: ...' şeklinde yaz.
               - Formülleri doğrudan temiz Unicode sembolleriyle yaz (Örn: 'F = m · a', 'E = m · c²', 'Δx = v · t').
               - Uygulamanın yerel PDF motoru bu metni otomatik olarak A4 sayfalarına, şık başlık stillerine, sayfa numaralarına ve estetik tasarıma dönüştürecektir.
            4. SOHBETTE SADECE KISA BİR AÇIKLAMA YAP:
               Sohbet metninde sadece dosyanın ne olduğunu anlatan 1-2 cümlelik sade bir açıklama yap (Örnek: "İstediğin PDF dokümanını hazırladım, aşağıdaki karttan inceleyebilir ve indirebilirsin."). Kodların veya belgenin tamamını ```file:dosya_adi.uzanti bloğunun içine koy. Uygulama bu kodu otomatik olarak gizleyip indirilebilir/önizlenebilir dosya kartı olarak sunacaktır.

            ÖZETLE: Bir insanla sohbet eder gibi doğal, kibar, güncel, hızlı ve sade ol.
            """.trimIndent())
        }


        val systemInstructionObj = JSONObject()
        val sysParts = JSONArray()
        sysParts.put(JSONObject().put("text", fullSystemPrompt))
        systemInstructionObj.put("parts", sysParts)
        rootJson.put("systemInstruction", systemInstructionObj)

        return rootJson.toString().toRequestBody(jsonMediaType)
    }

    private fun readFileAsBase64(uri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.size <= 15 * 1024 * 1024) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun readFileAsText(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (_: Throwable) {
            null
        }
    }
}

