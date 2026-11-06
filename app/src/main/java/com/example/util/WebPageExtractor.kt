package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

data class ExtractedWebContent(
    val url: String,
    val title: String?,
    val description: String?,
    val mainContent: String?,
    val platformType: String
)

object WebPageExtractor {
    private const val TAG = "WebPageExtractor"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val URL_PATTERN = Pattern.compile(
        "(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?)",
        Pattern.CASE_INSENSITIVE
    )

    fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val url = matcher.group(1)?.trimEnd('.', ',', ')', ']', '>', ';', '!', '?')
            if (!url.isNullOrBlank() && !urls.contains(url)) {
                urls.add(url)
            }
        }
        return urls
    }

    suspend fun fetchUrlContent(url: String, client: OkHttpClient): ExtractedWebContent? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(7500) {
            try {
                val lowerUrl = url.lowercase()
                when {
                    lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> {
                        fetchYouTubeContent(url, client)
                    }
                    lowerUrl.contains("open.spotify.com") -> {
                        fetchSpotifyContent(url, client)
                    }
                    else -> {
                        fetchGenericWebContent(url, client)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch content for $url", e)
                null
            }
        }
    }

    private fun fetchYouTubeContent(url: String, client: OkHttpClient): ExtractedWebContent {
        val oEmbedUrl = "https://www.youtube.com/oembed?url=" + java.net.URLEncoder.encode(url, "UTF-8") + "&format=json"
        var title: String? = null
        var author: String? = null

        try {
            val oembedReq = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val oembedResp = client.newCall(oembedReq).execute()
            if (oembedResp.isSuccessful) {
                val json = JSONObject(oembedResp.body?.string().orEmpty())
                title = json.optString("title")
                author = json.optString("author_name")
            }
        } catch (_: Exception) {}

        val htmlContent = fetchRawHtml(url, client)
        val ogDescription = extractMetaTag(htmlContent, "og:description") ?: extractMetaTag(htmlContent, "description")
        val ogTitle = extractMetaTag(htmlContent, "og:title") ?: title

        val contentBuilder = StringBuilder()
        if (!author.isNullOrBlank()) contentBuilder.append("Kanal / Yayıncı: $author\n")
        if (!ogDescription.isNullOrBlank()) contentBuilder.append("Açıklama:\n$ogDescription\n")

        return ExtractedWebContent(
            url = url,
            title = ogTitle ?: title ?: "YouTube Videosu / Kanalı",
            description = ogDescription,
            mainContent = contentBuilder.toString().ifBlank { "YouTube video veya kanal bağlantısı: $url" },
            platformType = "YouTube"
        )
    }

    private fun fetchSpotifyContent(url: String, client: OkHttpClient): ExtractedWebContent {
        val oEmbedUrl = "https://open.spotify.com/oembed?url=" + java.net.URLEncoder.encode(url, "UTF-8")
        var title: String? = null
        var description: String? = null

        try {
            val req = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                title = json.optString("title")
            }
        } catch (_: Exception) {}

        val htmlContent = fetchRawHtml(url, client)
        val ogTitle = extractMetaTag(htmlContent, "og:title") ?: title
        val ogDescription = extractMetaTag(htmlContent, "og:description") ?: extractMetaTag(htmlContent, "description")
        val trackArtist = extractMetaTag(htmlContent, "music:musician_description")

        val contentBuilder = StringBuilder()
        if (!ogTitle.isNullOrBlank()) contentBuilder.append("Spotify Başlığı: $ogTitle\n")
        if (!trackArtist.isNullOrBlank()) contentBuilder.append("Sanatçı: $trackArtist\n")
        if (!ogDescription.isNullOrBlank()) contentBuilder.append("Açıklama: $ogDescription\n")

        return ExtractedWebContent(
            url = url,
            title = ogTitle ?: "Spotify Çalma Listesi / Parça",
            description = ogDescription ?: description,
            mainContent = contentBuilder.toString().ifBlank { "Spotify bağlantısı: $url" },
            platformType = "Spotify"
        )
    }

    private fun fetchGenericWebContent(url: String, client: OkHttpClient): ExtractedWebContent {
        val html = fetchRawHtml(url, client)
        val title = extractTitleTag(html) ?: extractMetaTag(html, "og:title")
        val description = extractMetaTag(html, "og:description") ?: extractMetaTag(html, "description")
        val cleanText = extractReadableBody(html)

        return ExtractedWebContent(
            url = url,
            title = title,
            description = description,
            mainContent = cleanText.take(15000),
            platformType = "Web Sayfası"
        )
    }

    private fun fetchRawHtml(url: String, client: OkHttpClient): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr,en;q=0.9")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                resp.body?.string().orEmpty()
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractTitleTag(html: String): String? {
        val matcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
        return if (matcher.find()) cleanHtmlEntities(matcher.group(1).orEmpty().trim()) else null
    }

    private fun extractMetaTag(html: String, metaNameOrProperty: String): String? {
        val p1 = Pattern.compile("<meta[^>]+(?:name|property)=[\"']" + Pattern.quote(metaNameOrProperty) + "[\"'][^>]+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val m1 = p1.matcher(html)
        if (m1.find()) return cleanHtmlEntities(m1.group(1).orEmpty().trim())

        val p2 = Pattern.compile("<meta[^>]+content=[\"'](.*?)[\"'][^>]+(?:name|property)=[\"']" + Pattern.quote(metaNameOrProperty) + "[\"']", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val m2 = p2.matcher(html)
        if (m2.find()) return cleanHtmlEntities(m2.group(1).orEmpty().trim())

        return null
    }

    private fun extractReadableBody(html: String): String {
        if (html.isBlank()) return ""
        var cleaned = html

        // Remove scripts, styles, svg, and navigations
        cleaned = cleaned.replace(Regex("(?is)<script.*?>.*?</script>"), " ")
        cleaned = cleaned.replace(Regex("(?is)<style.*?>.*?</style>"), " ")
        cleaned = cleaned.replace(Regex("(?is)<svg.*?>.*?</svg>"), " ")
        cleaned = cleaned.replace(Regex("(?is)<header.*?>.*?</header>"), " ")
        cleaned = cleaned.replace(Regex("(?is)<footer.*?>.*?</footer>"), " ")
        cleaned = cleaned.replace(Regex("(?is)<nav.*?>.*?</nav>"), " ")

        // Replace tags with space
        cleaned = cleaned.replace(Regex("<[^>]+>"), " ")
        cleaned = cleanHtmlEntities(cleaned)
        // Normalize multiple spaces and line breaks
        cleaned = cleaned.replace(Regex("[ \\t]+"), " ")
        cleaned = cleaned.replace(Regex("(\\r?\\n[ \\t]*)+"), "\n")

        return cleaned.trim()
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
    }
}
