package com.androidagent.tool.network

import kotlin.text.Regex
import kotlin.text.RegexOption

/**
 * §13 minimal dependency-free HTML -> readable-text extractor.
 *
 * Tolerant tag-strip (NOT a spec-complete parser, NO DOM, NO JS, NO reader-mode).
 * Scope, frozen:
 *   - pull <title> as a leading line
 *   - remove <script>, <style>, <noscript>, <head> (non-title), comments, DOCTYPE
 *   - turn block boundaries into newlines
 *   - strip remaining tags
 *   - decode common HTML entities
 *   - normalize whitespace
 *
 * Jsoup is the documented upgrade path if this proves inadequate on real pages
 * (a separate, justified proposal — not an inline swap).
 */
internal object HtmlToText {

    private val DOCTYPE = "<!DOCTYPE[^>]*>".toRegex(RegexOption.IGNORE_CASE)
    private val COMMENT = "<!--.*?-->".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val SCRIPT = "<script\\b[^>]*>.*?</script>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val STYLE = "<style\\b[^>]*>.*?</style>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val NOSCRIPT = "<noscript\\b[^>]*>.*?</noscript>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val HEAD = "<head\\b[^>]*>.*?</head>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val BLOCKS =
        "</?(p|div|li|ul|ol|table|tr|td|th|h1|h2|h3|h4|h5|h6|blockquote|pre|article|section|header|footer|nav|form|br|hr)\\b[^>]*>"
            .toRegex(RegexOption.IGNORE_CASE)
    private val TITLE = "<title\\b[^>]*>(.*?)</title>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TAGS = "<[^>]+>".toRegex()
    private val NUM_ENTITY = "&#(\\d+);".toRegex()
    private val WS_RUN = "[ \\t\\r\\f\\v]+".toRegex()
    private val WS_LINE = " ?\\n ?".toRegex()
    private val WS_BLANK = "\n{3,}".toRegex()

    fun extract(html: String): String {
        val title = titleOf(html)

        var s = html
        s = DOCTYPE.replace(s, " ")
        s = COMMENT.replace(s, " ")
        s = SCRIPT.replace(s, " ")
        s = STYLE.replace(s, " ")
        s = NOSCRIPT.replace(s, " ")
        s = HEAD.replace(s, " ")
        s = BLOCKS.replace(s, "\n")
        s = TAGS.replace(s, " ")
        s = decodeEntities(s)
        s = normalizeWhitespace(s)

        val body = s.trim()
        return if (title.isNotEmpty()) "$title\n$body".trim() else body
    }

    private fun titleOf(html: String): String {
        val m = TITLE.find(html) ?: return ""
        return normalizeWhitespace(decodeEntities(m.groupValues[1])).trim()
    }

    private fun decodeEntities(s: String): String {
        var out = s
        out = out.replace("&nbsp;", " ")
        out = out.replace("&lt;", "<")
        out = out.replace("&gt;", ">")
        out = out.replace("&quot;", "\"")
        out = out.replace("&#39;", "'")
        out = out.replace("&apos;", "'")
        out = NUM_ENTITY.replace(out) { match: MatchResult ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 32..0x10FFFF) code.toChar().toString() else " "
        }
        out = out.replace("&amp;", "&")
        return out
    }

    private fun normalizeWhitespace(s: String): String {
        var out = s
        out = WS_RUN.replace(out, " ")
        out = WS_LINE.replace(out, "\n")
        out = WS_BLANK.replace(out, "\n\n")
        return out.trim()
    }
}
