package com.shadaeiou.stitchcounter.ui.pattern

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PatternFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPatternFromUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                else -> "https://$url"
            }
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) StitchCounter/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Server returned ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw Exception("Empty response from server")
            val contentType = response.body?.contentType()?.toString() ?: ""
            if (contentType.contains("application/pdf") || body.startsWith("%PDF")) {
                throw Exception("PDF files are not supported here. Use 'Upload PDF' to view PDFs with annotations.")
            }
            val plainText = extractTextFromHtml(body)
            findPatternStart(plainText)
        }
    }

    private fun extractTextFromHtml(html: String): String {
        var text = html
        // Remove <script> and <style> blocks entirely
        text = text.replace(
            Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            " ",
        )
        text = text.replace(
            Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            " ",
        )
        // Replace block-level tags with line breaks
        text = text.replace(
            Regex("</?(?:p|div|li|h[1-6]|tr|blockquote|pre|section|article|header|footer|main)[^>]*>",
                RegexOption.IGNORE_CASE),
            "\n",
        )
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        // Strip remaining HTML tags
        text = text.replace(Regex("<[^>]+>"), "")
        // Decode common HTML entities
        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#x?[0-9a-fA-F]+;"), "")
        // Normalise whitespace
        text = text.replace(Regex("[ \t]+"), " ")
        text = text.replace(Regex("(\\n[ \t]*)+"), "\n")
        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }

    // Heuristic: find where the knitting / crochet pattern actually starts and
    // trim any blog intro / copyright preamble that comes before it.
    private fun findPatternStart(text: String): String {
        val markers = listOf(
            // Materials/supplies header — reliably marks the start of a pattern
            Regex("(?im)^[ \t]*(?:materials?|supplies?|what you'?ll? need)[ \t]*:"),
            Regex("(?im)^[ \t]*(?:yarn|hook|needle|gauge|abbreviations?|notes?)[ \t]*:"),
            // Cast-on or foundation chain
            Regex("(?im)^[ \t]*(?:cast[ \t]+on|co)[ \t]+\\d+"),
            Regex("(?im)^[ \t]*(?:ch|chain)[ \t]+\\d+"),
            // First row / round
            Regex("(?im)^[ \t]*row[ \t]+1\\b"),
            Regex("(?im)^[ \t]*r(?:oun)?d\\.?[ \t]*1\\b"),
            Regex("(?im)^[ \t]*rnd\\.?[ \t]*1\\b"),
            // Common stitch abbreviations that indicate pattern body
            Regex("(?i)\\bk2tog\\b"),
            Regex("(?i)\\bssk\\b"),
            Regex("(?i)\\bsc[ \t]+\\d+\\b"),
            Regex("(?i)\\bdc[ \t]+\\d+\\b"),
            Regex("(?i)\\brepeat from \\*\\b"),
        )
        var earliest = Int.MAX_VALUE
        for (regex in markers) {
            val match = regex.find(text) ?: continue
            if (match.range.first < earliest) earliest = match.range.first
        }
        if (earliest == Int.MAX_VALUE) return text
        // Start from the beginning of whichever line contains the marker
        val lineStart = text.lastIndexOf('\n', earliest - 1).let { if (it < 0) 0 else it + 1 }
        return text.substring(lineStart).trim()
    }
}

// Convert plain text to simple HTML for the editor (preserves paragraphs)
fun plainTextToHtml(text: String): String {
    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return escaped
        .split("\n\n")
        .filter { it.isNotBlank() }
        .joinToString("") { para ->
            "<p>" + para.replace("\n", "<br>") + "</p>"
        }
}
