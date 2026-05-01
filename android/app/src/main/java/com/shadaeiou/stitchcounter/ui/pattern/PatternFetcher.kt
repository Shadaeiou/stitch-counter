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
            val mixed = extractMixedContent(cleaned)
            val withoutTrailingComments = trimTextComments(mixed)
            findPatternStart(withoutTrailingComments)
        }
    }

    // ── HTML-level comment removal ────────────────────────────────────────────

    private val COMMENT_SECTION_PATTERNS = listOf(
        Regex("""<(?:div|section|aside|article|ol|ul)[^>]+\bid\s*=\s*["'][^"']*\bcomments?\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bid\s*=\s*["']respond["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bid\s*=\s*["']discussion["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section|aside|ol|ul)[^>]+\bclass\s*=\s*["'][^"']*\bcomments?(?:-section|-area|-list|-block|-wrap(?:per)?)?\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bclass\s*=\s*["'][^"']*\bdiscussion\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
        Regex("""<(?:div|section)[^>]+\bclass\s*=\s*["'][^"']*\bcomment-respond\b[^"']*["']""",
            RegexOption.IGNORE_CASE),
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

    // ── HTML → mixed content (text + photo markers) ───────────────────────────
    // Extracts text while preserving absolute-URL images as [PHOTO:url] tokens
    // so they can be re-inserted as thumbnails in the final HTML.

    private fun extractMixedContent(html: String): String {
        var text = html
        // Remove scripts and styles
        text = text.replace(
            Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        text = text.replace(
            Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")

        // Capture img src before tag-stripping
        text = text.replace(Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)) { match ->
            val srcMatch = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(match.value)
            val src = srcMatch?.groupValues?.get(1) ?: return@replace ""
            val absoluteSrc = when {
                src.startsWith("https://") || src.startsWith("http://") -> src
                src.startsWith("//") -> "https:$src"
                else -> return@replace ""
            }
            "\n[PHOTO:$absoluteSrc]\n"
        }

        // Block elements → newlines
        text = text.replace(
            Regex("</?(?:p|div|li|h[1-6]|tr|blockquote|pre|section|article|header|footer|main)[^>]*>",
                RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        // Strip remaining tags
        text = text.replace(Regex("<[^>]+>"), "")
        // Decode entities
        text = text
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&nbsp;", " ").replace(Regex("&#x?[0-9a-fA-F]+;"), "")
        // Normalise whitespace
        text = text.replace(Regex("[ \t]+"), " ")
        text = text.replace(Regex("(\\n[ \t]*)+"), "\n")
        text = text.replace(Regex("\n{3,}"), "\n\n")
        // Isolate each photo marker as its own paragraph
        text = text.replace(Regex("\n?(\[PHOTO:[^\]]+\])\n?"), "\n\n$1\n\n")
        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }

    // ── Pattern-start heuristic ───────────────────────────────────────────────

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

// Convert mixed text (with [PHOTO:url] markers) to editor HTML.
// Text paragraphs are HTML-escaped; photo markers become tappable thumbnails.
fun plainTextToHtml(text: String): String {
    val photoMarker = Regex("""^\[PHOTO:(.+)\]$""")
    return text.split("\n\n")
        .filter { it.isNotBlank() }
        .joinToString("") { para ->
            val trimmed = para.trim()
            val m = photoMarker.matchEntire(trimmed)
            if (m != null) {
                val url = m.groupValues[1]
                """<div class="pattern-photo"><a href="$url"><img class="pattern-img" src="$url" alt=""></a></div>"""
            } else {
                val escaped = trimmed
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                "<p>${escaped.replace("\n", "<br>")}</p>"
            }
        }
}
