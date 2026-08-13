package com.samyak.blackboxmailt.ui

import android.text.Html
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatters are rebuilt when the device locale changes. Caching them in top-level vals would
 * freeze the locale at class-init time and format dates wrongly after a locale switch.
 */
private class DateFormatters(val locale: Locale) {
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
    val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", locale)
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yy", locale)
    val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", locale)
}

@Volatile
private var cachedFormatters: DateFormatters? = null

private fun formatters(): DateFormatters {
    val locale = Locale.getDefault()
    cachedFormatters?.takeIf { it.locale == locale }?.let { return it }
    return DateFormatters(locale).also { cachedFormatters = it }
}

/** Gmail's list timestamp: time for today, "Mar 14" this year, "3/14/24" beyond. */
fun formatListDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val formats = formatters()
    return when {
        dateTime.toLocalDate() == today -> formats.time.format(dateTime)
        dateTime.year == today.year -> formats.monthDay.format(dateTime)
        else -> formats.shortDate.format(dateTime)
    }
}

/** Full timestamp shown in the reading view header. */
fun formatFullDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return formatters().fullDate
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Turns an html body into a one-line preview for the list row. */
fun htmlToPreview(html: String): String =
    @Suppress("DEPRECATION")
    Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Wraps a message body in a responsive document so the WebView renders it the way the
 * Gmail app does: full width, images scaled down, colours matched to the app theme.
 */
fun buildMessageDocument(body: String, isHtml: Boolean, dark: Boolean): String {
    val textColor = if (dark) "#E3E3E3" else "#1F1F1F"
    val background = if (dark) "#131314" else "#FFFFFF"
    val linkColor = if (dark) "#A8C7FA" else "#0B57D0"
    val quoteBorder = if (dark) "#444746" else "#DADCE0"
    val content = if (isHtml) body else "<pre class=\"plain\">${escapeHtml(body)}</pre>"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          :root { color-scheme: ${if (dark) "dark" else "light"}; }
          html, body {
            margin: 0; padding: 16px 16px 40px 16px;
            background: $background; color: $textColor;
            font-family: -apple-system, Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 15px; line-height: 1.55;
            word-break: break-word; overflow-wrap: anywhere;
            -webkit-text-size-adjust: 100%;
          }
          img, video, iframe { max-width: 100% !important; height: auto !important; }
          table { max-width: 100% !important; }
          a { color: $linkColor; }
          pre.plain { white-space: pre-wrap; font-family: inherit; margin: 0; }
          blockquote {
            margin: 12px 0; padding-left: 12px;
            border-left: 2px solid $quoteBorder; color: $textColor; opacity: .8;
          }
        </style>
        </head>
        <body>$content</body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
