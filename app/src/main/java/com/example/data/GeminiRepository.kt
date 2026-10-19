package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.model.AttachedFile
import com.example.model.ChatMessage
import com.example.model.GroundingSource
import com.example.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Candidate models to automatically cycle through if one returns 404
    private val candidateModels = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-2.5-flash",
        "gemini-1.5-flash-latest",
        "gemini-2.0-flash-exp",
        "gemini-flash-lite-latest"
    )

    /**
     * Streams responses in real-time chunk-by-chunk using Gemini SSE streamGenerateContent.
     * Incorporates Google Search Grounding to research up-to-date web information.
     * Includes automatic 429 rate limit backoff, key pool failover, and search tool fallback.
     */
    val memoryManager = MemoryManager(context)
    val taskManager = TaskManager(context)
    val routineContextManager = RoutineContextManager(context)

    fun sendMessageStream(
        userPrompt: String,
        attachedFile: AttachedFile? = null,
        conversationHistory: List<ChatMessage> = emptyList(),
        persona: com.example.model.AssistantPersona = com.example.model.AssistantPersona.GENERAL,
        isWebSearchEnabled: Boolean = true
    ): Flow<StreamUpdate> = flow {
        val validKeys = apiKeyManager.getValidKeysWithIndices()

        if (validKeys.isEmpty()) {
            emit(
                StreamUpdate(
                    accumulatedText = "Merhaba! Sohbet sistemi hazır.\n\n" +
                            "3 API anahtarı tanımlamak için sağ üstteki 3 nokta menüsünden 'API & Model Ayarları' seçeneğine tıklayabilirsiniz. " +
                            "Model olarak gemini-2.0-flash kullanılmaktadır.",
                    failoverNotice = null,
                    isDone = true
                )
            )
            return@flow
        }

        var failoverNotice: String? = null
        val attemptsCount = validKeys.size
        var currentActive = apiKeyManager.getActiveKey() ?: validKeys.first()

        val preferred = "gemini-2.0-flash"
        val modelsToTry = (listOf(preferred) + candidateModels).distinct()

        var useSearchGrounding = isWebSearchEnabled

        attemptLoop@ for (attempt in 0 until attemptsCount) {
            val keyIndex = currentActive.first
            val keyValue = currentActive.second

            var modelIndex = 0
            while (modelIndex < modelsToTry.size) {
                val currentModel = modelsToTry[modelIndex]
                val cleanModel = currentModel.removePrefix("models/")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?alt=sse&key=$keyValue"

                val requestBody = buildRequestBody(userPrompt, attachedFile, conversationHistory, useSearchGrounding, persona)
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

                        // If 404: Try next model
                        if (code == 404 && modelIndex < modelsToTry.size - 1) {
                            modelIndex++
                            val nextModel = modelsToTry[modelIndex]
                            val switchNote = "Model '$currentModel' 404 verdi. Otomatik olarak '$nextModel' deneniyor..."
                            failoverNotice = if (failoverNotice == null) switchNote else "$failoverNotice | $switchNote"
                            apiKeyManager.setSelectedModel(nextModel)
                            continue
                        }

                        // If 429 (Quota / Rate Limit) or 503 (Overloaded)
                        val is429or503 = code == 429 || code == 503 ||
                                lowerErr.contains("quota") || lowerErr.contains("rate") ||
                                lowerErr.contains("resource_exhausted") || lowerErr.contains("overloaded")

                        if (is429or503) {
                            // First priority: If another API key is available in the pool, switch to it!
                            if (attempt < attemptsCount - 1) {
                                val reason = if (code == 503 || lowerErr.contains("overloaded")) "Sunucu yoğun (503)" else "Kota doldu (429)"
                                val nextKey = apiKeyManager.switchToNextKey(keyIndex, reason)
                                if (nextKey != null) {
                                    failoverNotice = "Anahtar #${keyIndex + 1} ($reason). Otomatik olarak Anahtar #${nextKey.first + 1}'e geçildi."
                                    currentActive = nextKey
                                    continue@attemptLoop
                                }
                            }

                            // If we have only 1 key and Search Grounding was active, search tool has lower quota.
                            // Retry once without search tool to avoid blocking the user completely!
                            if (useSearchGrounding) {
                                useSearchGrounding = false
                                failoverNotice = "Canlı arama kotası (429) doldu. Standart model ile yanıt alınıyor..."
                                delay(1200)
                                continue // Retry this model without search tool
                            }

                            // If single key rate-limited (RPM), wait briefly and retry once
                            if (attempt == 0 && attemptsCount == 1) {
                                delay(2000)
                            }
                        }

                        val specificMsg = when {
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
                                                val text = part.getString("text")
                                                accumulated.append(text)
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

        val preferred = apiKeyManager.status.value.selectedModel.ifEmpty { "gemini-2.0-flash" }
        val modelsToTry = (listOf(preferred) + candidateModels).distinct()

        var useSearchGrounding = true

        attemptLoop@ for (attempt in 0 until attemptsCount) {
            val keyIndex = currentActive.first
            val keyValue = currentActive.second

            var modelIndex = 0
            while (modelIndex < modelsToTry.size) {
                val currentModel = modelsToTry[modelIndex]
                val cleanModel = currentModel.removePrefix("models/")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$keyValue"

                try {
                    val requestBody = buildRequestBody(userPrompt, attachedFile, conversationHistory, useSearchGrounding)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBodyString = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        if (response.code == 404 && modelIndex < modelsToTry.size - 1) {
                            modelIndex++
                            val nextModel = modelsToTry[modelIndex]
                            failoverNotice = "Model '$currentModel' 404 verdi. Otomatik olarak '$nextModel' modeline geçildi."
                            apiKeyManager.setSelectedModel(nextModel)
                            continue
                        }

                        val errorMsg = responseBodyString.lowercase()
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

    private fun buildRequestBody(
        userPrompt: String,
        attachedFile: AttachedFile?,
        conversationHistory: List<ChatMessage>,
        includeSearchGrounding: Boolean = true,
        persona: com.example.model.AssistantPersona = com.example.model.AssistantPersona.GENERAL
    ): RequestBody {
        val rootJson = JSONObject()
        val contentsArray = JSONArray()

        val recentHistory = conversationHistory.takeLast(10)
        for (msg in recentHistory) {
            if (msg.text.isBlank()) continue
            val contentObj = JSONObject()
            contentObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
            val partsArray = JSONArray()
            partsArray.put(JSONObject().put("text", msg.text))
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
                val base64Data = readFileAsBase64(fileUri)
                if (base64Data != null) {
                    val inlineDataObj = JSONObject()
                        .put("mimeType", "application/pdf")
                        .put("data", base64Data)
                    currentParts.put(JSONObject().put("inlineData", inlineDataObj))
                }
            } else if (attachedFile.isImage) {
                val base64Data = readFileAsBase64(fileUri)
                if (base64Data != null) {
                    val mime = if (attachedFile.mimeType.isNotBlank()) attachedFile.mimeType else "image/jpeg"
                    val inlineDataObj = JSONObject()
                        .put("mimeType", mime)
                        .put("data", base64Data)
                    currentParts.put(JSONObject().put("inlineData", inlineDataObj))
                }
            } else if (attachedFile.isText) {
                val textContent = readFileAsText(fileUri)
                if (textContent != null) {
                    promptText = "[Ekli Dosya: ${attachedFile.name}]\nİçerik:\n$textContent\n---\n[Kullanıcı Sorusu/Talimatı]: ${userPrompt.ifBlank { "Bu dosyanın içeriğini analiz et ve açıkla." }}"
                }
            } else {
                val base64Data = readFileAsBase64(fileUri)
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

        val memoryText = memoryManager.getFormattedMemoriesForSystemPrompt()
        val rulesText = memoryManager.getFormattedRulesForSystemPrompt()
        val routineText = routineContextManager.routineSummary.value

        val fullSystemPrompt = buildString {
            append("Sen son derece bilgili, doğal, samimi, Türkçe dilinde usta ve internetteki en güncel bilgilere erişebilen çok yönlü bir kişisel yapay zeka asistanısın.\n\n")
            if (rulesText.isNotBlank()) {
                append(rulesText).append("\n\n")
            }
            if (memoryText.isNotBlank()) {
                append(memoryText).append("\n\n")
            }
            if (routineText.isNotBlank()) {
                append(routineText).append("\n\n")
            }
            append("""
            İNTERNET VE GÜNCEL BİLGİ ARAŞTIRMA KURALI (ÇOK ÖNEMLİ):
            1. Kullanıcının sorusu güncel olaylar, tarih, saat, son haberler, hava durumu, piyasa fiyatları, canlı maçlar, yeni teknolojiler, kişiler, olaylar veya araştırma gerektiren herhangi bir konu içeriyorsa; Google Search aracını kullanarak internette kapsamlı, derinlemesine ve en güncel aramaları yap.
            2. Web aramalarından elde ettiğin en doğru ve güncel bilgileri sentezleyerek kullanıcıya net, doğru ve doğrudan aktar.
            3. Kullanıcının sorduğu konu hakkında bilgilerin en son halini sunmaktan çekinme ve kaynakların doğruluğunu gözet.

            11. SINIF & TYT-AYT SORU ÇÖZÜMÜ VE EĞİTİM REHBERİ:
            1. Kullanıcı matematik, geometri, fizik, kimya, biyoloji, Türkçe veya TYT-AYT sınav sorusu sorduğunda veya sorunun fotoğrafını attığında; soruyu aceleye getirmeden adım adım çöz.
            2. Önce sorudaki verilenleri ve isteneni belirle. Hangi konunun hangi teorem, kural veya formülünün kullanıldığını açıkça yaz.
            3. Çözüm adımlarını mantık sırasına göre numaralandır veya alt alta sırala. Matematiksel işlemleri net ve okunaklı yap.
            4. Şıklı sorularda doğru cevabın hangi şık olduğunu ve neden o şık olduğunu gerekçesiyle açıkla; varsa soru kökündeki püf noktaları belirt.

            KULLANICI DOSYALARI ÜZERİNDE DÜZENLEME VE GELİŞTİRME:
            1. Kullanıcı bir dosya (HTML, Python, CSS, JavaScript, JSON, metin vb.) yükleyip üzerinde değişiklik, geliştirme veya hata düzeltmesi istediğinde; dosyanın içeriğini dikkatlice analiz et.
            2. İstenen değişiklikleri eksiksiz uygulayarak güncellenmiş dosyanın tamamını yine ```file:dosya_adi.uzanti formatında doğrudan indirilebilir yeni dosya olarak teslim et.

            DOĞAL VE SADE SOHBET:
            1. Varsayılan tarzın sade, akıcı ve tıpkı bir insan gibi doğal olmalıdır.
            2. Normal sohbetlerde, selamlaşmalarda (merhaba, selam, nasılsın, naber vb.) ve kısa/genel sorularda KESİNLİKLE başlık (#), kutucuk (>), ara çizgi (---) veya altı çizili kelimeler KULLANMA. Doğrudan, sade ve net bir dille yanıt ver.
            3. BİÇİMLENDİRME ENFLASYONUNDAN KAÇIN: Cümlelerin içine gereksiz yere kalın (**), altı çizili (__) veya semboller sıkıştırma. Metin sade ve göz yormayan bir akışta olmalı.
            4. DOSYA ANALİZİ: Kullanıcı PDF, metin, kod, tablo veya başka bir dosya paylaştığında dosyanın içeriğini ayrıntılı, dikkatli ve doğru şekilde analiz et.

            KOD VE İNDİRİLEBİLİR DOSYA KURALI (MANDATORY & EN ÖNEMLİ KURAL):
            1. ASLA VE ASLA SOHBETTE HAM KOD YAZMA:
               Kullanıcı HTML, web sayfası, Python, JavaScript, CSS, Kotlin, SVG, JSON veya herhangi bir kod, betik veya belge istediğinde; kodları KESİNLİKLE sohbette doğrudan ham kod bloğu olarak verme!
            2. HER ZAMAN İNDİRİLEBİLİR DOSYA OLARAK VER:
               Her zaman ve istisnasız ```file:dosya_adi.uzanti formatında sun:
               - HTML için: ```file:index.html (veya sayfa_adi.html)
               - Python için: ```file:script.py
               - JavaScript için: ```file:script.js
               - CSS için: ```file:style.css
               - SVG çizimi için: ```file:cizim.svg
               - JSON verisi için: ```file:veri.json
               - Metin/not belgesi için: ```file:notlar.txt
               - Diğer dosyalar için: ```file:dosya_adi.uzanti
            3. SOHBETTE SADECE KISA BİR AÇIKLAMA YAP:
               Sohbet metninde sadece dosyanın ne olduğunu anlatan 1-2 cümlelik sade bir açıklama yap (Örnek: "İstediğin HTML sayfasını hazırladım, aşağıdaki karttan tam ekran önizleyebilir veya indirebilirsin."). Kodların tamamını ```file:dosya_adi.uzanti bloğunun içine koy. Uygulama bu kodu otomatik olarak gizleyip indirilebilir/önizlenebilir dosya kartı olarak sunacaktır.

            ÖZETLE: Bir insanla sohbet eder gibi doğal, kibar, güncel ve sade ol.
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
