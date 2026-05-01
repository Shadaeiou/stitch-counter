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
            val cleaned = removeCommentSections(body)
            val plainText = extractTextFromHtml(cleaned)
            val withoutTrailingComments = trimTextComments(plainText)
            findPatternStart(withoutTrailingComments)
        }
    }

    // ── HTML-level comment removal ────────────────────────────────────────────
    // Finds the first HTML element whose id or class strongly indicates a
    // comment section and discards everything from that point onward.

    private val COMMENT_SECTION_PATTERNS = listOf(
        // id-based (most reliable)
        Regex("""<(?:div|section|aside|article|ol|ul)[^>]+\bid\s*=\s*["'][^"']*\bcomments?\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bid\s*=\s*["']respond["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bid\s*=\s*["']discussion["']""",
            RegexOption.IGNORE_CASE),
        // class-based
        Regex("""<(?:div|section|aside|ol|ul)[^>]+\bclass\s*=\s*["'][^"']*\bcomments?(?:-section|-area|-list|-block|-wrap(?:per)?)?\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bclass\s*=\s*["'][^"']*\bdiscussion\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bclass\s*=\s*["'][^"']*\bcomment-respond\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
        // Disqus embed
        Regex("""<div[^>]+\bid\s*=\s*["']disqus_thread["']""",
            RegexOption.IGNORE_CASE),
    )

    private fun removeCommentSections(html: String): String {
        var earliest = Int.MAX_VALUE
        for (pattern in COMMENT_SECTION_PATTERNS) {
            val idx = pattern.find(html)?.range?.first ?: continue
            if (idx < earliest) earliest = idx
        }
        return if (earliest < html.length) html.substring(0, earliest) else html
    }

    // ── Plain-text-level comment trimming ────────────────────────────────────
    // Fallback: after tag-stripping, look for text phrases that mark the start
    // of the reader-comment section and drop everything after them.

    private val TEXT_COMMENT_MARKERS = listOf(
        Regex("""(?im)^[ \t]*Leave (a )?[Rr]eply\b"""),
        Regex("""(?im)^[ \t]*Leave (a )?[Cc]omment\b"""),
        Regex("""(?im)^[ \t]*Post (a )?[Cc]omment\b"""),
        Regex("""(?im)^[ \t]*Add (a )?[Cc]omment\b"""),
        Regex("""(?im)^[ \t]*\d+\s+[Cc]omments?\s*$"""),
        Regex("""(?im)^[ \t]*[Cc]omments?\s*\(\d+\)\s*$"""),
        Regex("""(?im)^[ \t]*Join the [Cc]onversation\b"""),
        Regex("""(?im)^[ \t]*\d+\s+[Rr]espond(?:s|ed)?\b"""),
        Regex("""(?im)^[ \t]*Reader [Cc]omments?\b"""),
        Regex("""(?im)^[ \t]*Share your thoughts?\b"""),
    )

    private fun trimTextComments(text: String): String {
        var earliest = Int.MAX_VALUE
        for (pattern in TEXT_COMMENT_MARKERS) {
            val idx = pattern.find(text)?.range?.first ?: continue
            if (idx < earliest) earliest = idx
        }
        return if (earliest < text.length) text.substring(0, earliest).trim() else text
    }

    // ── HTML → plain text ─────────────────────────────────────────────────────

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

    // ── Pattern-start heuristic ───────────────────────────────────────────────
    // Trim any blog intro / copyright preamble that comes before the actual
    // knit / crochet instructions.

    private fun findPatternStart(text: String): String {
        val markers = listOf(
            Regex("(?im)^[ \t]*(?:materials?|supplies?|what you'?ll? need)[ \t]*:"),
            Regex("(?im)^[ \t]*(?:yarn|hook|needle|gauge|abbreviations?|notes?)[ \t]*:"),
            Regex("(?im)^[ \t]*(?:cast[ \t]+on|co)[ \t]+\\d+"),
            Regex("(?im)^[ \t]*(?:ch|chain)[ \t]+\\d+"),
            Regex("(?im)^[ \t]*row[ \t]+1\\b"),
            Regex("(?im)^[ \t]*r(?:oun)?d\\.?[ \t]*1\\b"),
            Regex("(?im)^[ \t]*rnd\\.?[ \t]*1\\b"),
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
        val lineStart = text.lastIndexOf('\n', earliest - 1).let { if (it < 0) 0 else it + 1 }
        return text.substring(lineStart).trim()
    }
}

// Convert plain text to simple HTML paragraphs for the editor.
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
