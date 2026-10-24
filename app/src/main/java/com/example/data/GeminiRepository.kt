package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.model.AttachedFile
import com.example.model.ChatMessage
import com.example.model.GroundingSource
import com.example.model.MessageSender
import com.example.util.FileExporter
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
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Candidate models to automatically cycle through for maximum speed and reliability (Flash Lite Latest prioritized)
    private val candidateModels = listOf(
        "gemini-2.0-flash-lite",
        "gemini-flash-lite-latest",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.5-flash"
    )

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

        var failoverNotice: String? = null
        val attemptsCount = validKeys.size
        var currentActive = apiKeyManager.getActiveKey() ?: validKeys.first()

        // Ultra-fast Dynamic Routing: Use flash-lite for instantaneous sub-300ms chat/Q&A,
        // and switch to flash when full file editing or heavy code is detected
        val isFileTask = attachedFile != null || isPromptRelatedToFiles(userPrompt, attachedFile)
        val defaultModel = apiKeyManager.status.value.selectedModel.ifEmpty { "gemini-2.0-flash-lite" }
        val preferred = if (isFileTask && (defaultModel.contains("flash-lite") || defaultModel.contains("lite"))) "gemini-2.0-flash" else defaultModel

        val modelsToTry = (listOf(preferred) + candidateModels).distinct()

        // Only run search tool if web search is enabled AND prompt actually contains time/news/lookup intent
        var useSearchGrounding = isWebSearchEnabled && isSearchWarranted(userPrompt)

        // Use instant in-memory context and memories for lightning-fast responses (Firestore snapshot listeners keep them live in real-time)
        val freshRoutineContext = routineContextManager.routineSummary.value
        val freshMemories = memoryManager.getFormattedMemoriesForSystemPrompt()
        val freshRules = memoryManager.getFormattedRulesForSystemPrompt()

        attemptLoop@ for (attempt in 0 until attemptsCount) {
            val keyIndex = currentActive.first
            val keyValue = currentActive.second

            var modelIndex = 0
            while (modelIndex < modelsToTry.size) {
                val currentModel = modelsToTry[modelIndex]
                val cleanModel = currentModel.removePrefix("models/")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?alt=sse&key=$keyValue"

                val requestBody = buildRequestBody(
                    userPrompt = userPrompt,
                    attachedFile = attachedFile,
                    conversationHistory = conversationHistory,
                    includeSearchGrounding = useSearchGrounding,
                    persona = persona,
                    freshRoutineContext = freshRoutineContext,
                    freshMemoriesText = freshMemories,
                    freshRulesText = freshRules
                )
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache")
                    .post(requestBody)
                    .build()

                var callSuccess = false
                try {
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        val code = response.code
                        val errBody = response.body?.string().orEmpty()
                        val lowerErr = errBody.lowercase()

                        // If 404, 429, or 503: Try next model or failover
                        val is429or503 = code == 429 || code == 503 ||
                                lowerErr.contains("quota") || lowerErr.contains("rate") ||
                                lowerErr.contains("resource_exhausted") || lowerErr.contains("overloaded") ||
                                code == 404

                        if (is429or503) {
                            // 1. Try next model if available
                            if (modelIndex < modelsToTry.size - 1) {
                                modelIndex++
                                val nextModel = modelsToTry[modelIndex]
                                val reason = when {
                                    code == 503 || lowerErr.contains("overloaded") -> "Sunucu Yoğun (503)"
                                    code == 429 -> "Kota Sınırı (429)"
                                    else -> "Bulunamadı (404)"
                                }
                                failoverNotice = "Model '$currentModel' $reason. Otomatik olarak '$nextModel' modeline geçiliyor..."
                                apiKeyManager.setSelectedModel(nextModel)
                                delay(800)
                                continue
                            }

                            // 2. If another API key is available in the pool, switch to it!
                            if (attempt < attemptsCount - 1) {
                                val reason = if (code == 503 || lowerErr.contains("overloaded")) "Sunucu yoğun (503)" else "Kota sınırı (429)"
                                val nextKey = apiKeyManager.switchToNextKey(keyIndex, reason)
                                if (nextKey != null) {
                                    failoverNotice = "Anahtar #${keyIndex + 1} ($reason). Otomatik olarak Anahtar #${nextKey.first + 1}'e geçildi."
                                    currentActive = nextKey
                                    delay(1000)
                                    continue@attemptLoop
                                }
                            }

                            // 3. Retry without search grounding if active
                            if (useSearchGrounding) {
                                useSearchGrounding = false
                                failoverNotice = "Arama aracı geçici olarak meşgul. Standart modda tekrar deneniyor..."
                                delay(1000)
                                continue
                            }
                        }

                        val specificMsg = when {
                            code == 503 -> "HTTP 503 (Sunucu Yoğun): Google Gemini sunucuları şu anda çok yoğun veya geçici olarak bakımda. Lütfen 5-10 saniye sonra tekrar deneyin."
                            code == 429 -> "HTTP 429 (Kota Sınırı): Gemini dakikalık istek sınırı doldu. Birkaç saniye sonra tekrar deneyin veya menüden yedek API anahtarları ekleyin."
                            code == 404 -> "HTTP 404: Model ($cleanModel) bulunamadı. Lütfen API anahtarınızın geçerli olduğundan emin olun."
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

                    callSuccess = true
                    apiKeyManager.setSelectedModel(cleanModel)

                    val reader = BufferedReader(InputStreamReader(bodyStream, Charsets.UTF_8))
                    val accumulated = StringBuilder()
                    val accumulatedSources = mutableListOf<GroundingSource>()
                    val accumulatedQueries = mutableListOf<String>()
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
                                            failoverNotice = failoverNotice,
                                            isDone = false
                                        )
                                    )
                                } catch (e: Exception) {
                                    // Ignore partial chunk parsing
                                }
                            }
                        }
                    }

                    val finalOutput = accumulated.toString()
                    emit(
                        StreamUpdate(
                            accumulatedText = finalOutput.ifBlank { "Yanıt alınamadı." },
                            groundingSources = accumulatedSources.toList(),
                            searchQueries = accumulatedQueries.toList(),
                            failoverNotice = failoverNotice,
                            isDone = true
                        )
                    )
                    return@flow

                } catch (e: Exception) {
                    if (!callSuccess && attempt < attemptsCount - 1) {
                        val nextKey = apiKeyManager.switchToNextKey(keyIndex, e.localizedMessage ?: "Bağlantı hatası")
                        if (nextKey != null) {
                            failoverNotice = "Anahtar #${keyIndex + 1} başarısız oldu. Anahtar #${nextKey.first + 1}'e devredildi."
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
        }

        emit(
            StreamUpdate(
                accumulatedText = "",
                error = "Tüm 3 API anahtarının kotası tükendi veya modeller yanıt vermedi. Lütfen API anahtarlarınızı kontrol edin.",
                isDone = true
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(
        userPrompt: String,
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

        var failoverNotice: String? = null
        val attemptsCount = validKeys.size
        var currentActive = apiKeyManager.getActiveKey() ?: validKeys.first()

        val isFileTask = attachedFile != null || isPromptRelatedToFiles(userPrompt, attachedFile)
        val defaultModel = apiKeyManager.status.value.selectedModel.ifEmpty { "gemini-2.0-flash-lite" }
        val preferred = if (isFileTask && (defaultModel.contains("flash-lite") || defaultModel.contains("lite"))) "gemini-2.0-flash" else defaultModel
        val modelsToTry = (listOf(preferred) + candidateModels).distinct()

        var useSearchGrounding = isSearchWarranted(userPrompt)

        // Use instant in-memory context and memories for lightning-fast responses
        val freshRoutineContext = routineContextManager.routineSummary.value
        val freshMemories = memoryManager.getFormattedMemoriesForSystemPrompt()
        val freshRules = memoryManager.getFormattedRulesForSystemPrompt()

        attemptLoop@ for (attempt in 0 until attemptsCount) {
            val keyIndex = currentActive.first
            val keyValue = currentActive.second

            var modelIndex = 0
            while (modelIndex < modelsToTry.size) {
                val currentModel = modelsToTry[modelIndex]
                val cleanModel = currentModel.removePrefix("models/")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$keyValue"

                try {
                    val requestBody = buildRequestBody(
                        userPrompt = userPrompt,
                        attachedFile = attachedFile,
                        conversationHistory = conversationHistory,
                        includeSearchGrounding = useSearchGrounding,
                        freshRoutineContext = freshRoutineContext,
                        freshMemoriesText = freshMemories,
                        freshRulesText = freshRules
                    )
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBodyString = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val errorMsg = responseBodyString.lowercase()

                        if (response.code == 404 && modelIndex < modelsToTry.size - 1) {
                            modelIndex++
                            val nextModel = modelsToTry[modelIndex]
                            failoverNotice = "Model '$currentModel' 404 verdi. Otomatik olarak '$nextModel' modeline geçildi."
                            apiKeyManager.setSelectedModel(nextModel)
                            continue
                        }

                        val is429or503 = response.code == 429 || response.code == 503 ||
                                errorMsg.contains("quota") || errorMsg.contains("rate") ||
                                errorMsg.contains("resource_exhausted") || errorMsg.contains("overloaded")

                        if (is429or503) {
                            if (attempt < attemptsCount - 1) {
                                val reason = if (response.code == 503 || errorMsg.contains("overloaded")) "Sunucu yoğun (503)" else "Kota doldu (429)"
                                val nextKey = apiKeyManager.switchToNextKey(keyIndex, reason)
                                if (nextKey != null) {
                                    failoverNotice = "Anahtar #${keyIndex + 1} ($reason). Otomatik olarak Anahtar #${nextKey.first + 1}'e geçildi."
                                    currentActive = nextKey
                                    continue@attemptLoop
                                }
                            }

                            if (useSearchGrounding) {
                                useSearchGrounding = false
                                delay(1200)
                                continue
                            }
                        }

                        val errText = when {
                            response.code == 429 -> "HTTP 429 (Kota Sınırı): İstek kotası doldu. Lütfen biraz bekleyin veya yedek anahtar ekleyin."
                            response.code == 404 -> "HTTP 404: Model ($cleanModel) bulunamadı."
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

                        apiKeyManager.setSelectedModel(cleanModel)
                        return@withContext Result.success(
                            SendMessageResult(
                                message = ChatMessage(
                                    sender = MessageSender.AI,
                                    text = replyText.ifBlank { "Yanıt alınamadı." },
                                    groundingSources = sources,
                                    searchQueries = queries
                                ),
                                failoverNotice = failoverNotice
                            )
                        )
                    }
                } catch (e: Exception) {
                    if (attempt < attemptsCount - 1) {
                        val nextKey = apiKeyManager.switchToNextKey(keyIndex, e.localizedMessage ?: "Bağlantı hatası")
                        if (nextKey != null) {
                            failoverNotice = "Anahtar #${keyIndex + 1} başarısız oldu. Anahtar #${nextKey.first + 1}'e devredildi."
                            currentActive = nextKey
                            continue@attemptLoop
                        }
                    }
                    return@withContext Result.failure(e)
                }
            }
        }

        Result.failure(Exception("Tüm 3 API anahtarının kotası tükendi veya modeller yanıt vermedi."))
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
        attachedFile: AttachedFile?,
        conversationHistory: List<ChatMessage>
    ): Map<String, String> {
        val filesMap = mutableMapOf<String, String>()

        // 1. Chronologically scan conversation history for user files and AI generated code
        for (msg in conversationHistory) {
            if (msg.sender == MessageSender.USER && msg.attachedFile != null) {
                val f = msg.attachedFile
                val text = f.cachedContent
                    ?: com.example.util.FileUtils.readFileContent(context, f)
                if (!text.isNullOrBlank()) {
                    filesMap[f.name] = if (text.length > 80000) text.take(80000) + "\n[... Dosya kısaltıldı ...]" else text
                }
            } else if (msg.sender == MessageSender.AI) {
                for (gen in msg.generatedFiles) {
                    if (gen.content.isNotBlank()) {
                        filesMap[gen.fileName] = gen.content
                    }
                }
                if (msg.text.contains("```file:")) {
                    val extracted = FileExporter.extractGeneratedFiles(msg.text)
                    for (ext in extracted) {
                        if (ext.content.isNotBlank()) {
                            filesMap[ext.fileName] = ext.content
                        }
                    }
                }
            }
        }

        // 2. Add current turn attached file if readable text
        if (attachedFile != null) {
            val currentText = attachedFile.cachedContent
                ?: com.example.util.FileUtils.readFileContent(context, attachedFile)
            if (!currentText.isNullOrBlank()) {
                filesMap[attachedFile.name] = if (currentText.length > 80000) currentText.take(80000) + "\n[... Dosya kısaltıldı ...]" else currentText
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
        attachedFile: AttachedFile?,
        conversationHistory: List<ChatMessage>,
        includeSearchGrounding: Boolean = true,
        persona: com.example.model.AssistantPersona = com.example.model.AssistantPersona.GENERAL,
        freshRoutineContext: String? = null,
        freshMemoriesText: String? = null,
        freshRulesText: String? = null
    ): RequestBody {
        val rootJson = JSONObject()
        val contentsArray = JSONArray()

        val activeFiles = collectActiveProjectFiles(attachedFile, conversationHistory)

        // Optimized token budget and generation tuning for maximum speed, accuracy & coding excellence
        val genConfig = JSONObject().apply {
            put("temperature", 0.6)
            put("topP", 0.95)
            put("topK", 40)
            put("maxOutputTokens", 4096)
        }
        rootJson.put("generationConfig", genConfig)

        // Keep compact rolling window (last 6 messages) for minimal token latency
        val recentHistory = conversationHistory.takeLast(6)
        for (msg in recentHistory) {
            val contentObj = JSONObject()
            contentObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
            val partsArray = JSONArray()

            val messageText = msg.text.ifBlank {
                if (msg.attachedFile != null) "[Ekli Dosya: ${msg.attachedFile.name}]" else ""
            }

            if (messageText.isBlank()) continue
            partsArray.put(JSONObject().put("text", messageText))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }

        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()

        var promptText = userPrompt

        if (attachedFile != null) {
            val fileUri = Uri.parse(attachedFile.uri)
            if (attachedFile.isPdf) {
                val base64Data = attachedFile.cachedBase64
                    ?: com.example.util.FileUtils.readFileBase64(context, attachedFile)
                    ?: readFileAsBase64(fileUri)
                if (base64Data != null) {
                    val inlineDataObj = JSONObject()
                        .put("mimeType", "application/pdf")
                        .put("data", base64Data)
                    currentParts.put(JSONObject().put("inlineData", inlineDataObj))
                }
            } else if (attachedFile.isImage) {
                val base64Data = attachedFile.cachedBase64
                    ?: com.example.util.FileUtils.readFileBase64(context, attachedFile)
                    ?: readFileAsBase64(fileUri)
                if (base64Data != null) {
                    val mime = if (attachedFile.mimeType.isNotBlank()) attachedFile.mimeType else "image/jpeg"
                    val inlineDataObj = JSONObject()
                        .put("mimeType", mime)
                        .put("data", base64Data)
                    currentParts.put(JSONObject().put("inlineData", inlineDataObj))
                }
            } else if (attachedFile.isText) {
                val textContent = activeFiles[attachedFile.name]
                    ?: attachedFile.cachedContent
                    ?: com.example.util.FileUtils.readFileContent(context, attachedFile)
                    ?: readFileAsText(fileUri)
                if (textContent != null) {
                    promptText = "[Ekli Dosya: ${attachedFile.name}]\nİçerik:\n$textContent\n---\n[Kullanıcı Sorusu/Talimatı]: ${userPrompt.ifBlank { "Bu dosyanın içeriğini analiz et ve açıkla." }}"
                }
            } else {
                val base64Data = attachedFile.cachedBase64
                    ?: com.example.util.FileUtils.readFileBase64(context, attachedFile)
                    ?: readFileAsBase64(fileUri)
                if (base64Data != null) {
                    val inlineDataObj = JSONObject()
                        .put("mimeType", attachedFile.mimeType.ifBlank { "application/octet-stream" })
                        .put("data", base64Data)
                    currentParts.put(JSONObject().put("inlineData", inlineDataObj))
                }
            }
        }

        currentParts.put(JSONObject().put("text", promptText.ifBlank { "Dosyayı analiz et." }))
        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)
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

            val shouldIncludeFullFiles = isPromptRelatedToFiles(userPrompt, attachedFile)
            if (activeFiles.isNotEmpty() && shouldIncludeFullFiles) {
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
            } else if (activeFiles.isNotEmpty()) {
                append("Mevcut Proje Dosyaları: ${activeFiles.keys.joinToString(", ")} (Dosya düzenlemesi istendiğinde tüm kodlar yüklenecektir)\n\n")
            }

            append("""
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
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun readFileAsText(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}

