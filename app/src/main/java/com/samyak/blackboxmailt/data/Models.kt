package com.samyak.blackboxmailt.data

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** A domain available on mail.tm, e.g. `indigobook.com`. */
data class MailDomain(
    val id: String,
    val domain: String,
    val isActive: Boolean,
    val isPrivate: Boolean
)

/** An email participant (`from`, `to`, `cc`, `bcc`). */
data class MailAddress(
    val name: String,
    val address: String
) {
    /** Gmail shows the display name when present, otherwise the local part of the address. */
    val displayName: String
        get() = when {
            name.isNotBlank() -> name
            address.contains('@') -> address.substringBefore('@')
            else -> address.ifBlank { "Unknown" }
        }

    val initial: String
        get() = displayName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
}

/** A temporary mail.tm account. */
data class MailAccount(
    val id: String,
    val address: String,
    val quota: Long = 0L,
    val used: Long = 0L,
    val createdAt: Long = 0L
)

/** Credentials we persist locally so the inbox survives app restarts. */
data class StoredAccount(
    val id: String,
    val address: String,
    val password: String,
    val createdAt: Long
)

data class TokenResult(val id: String, val token: String)

/** Row model for the inbox list (`GET /messages`). */
data class MessageSummary(
    val id: String,
    val accountId: String,
    val from: MailAddress,
    val to: List<MailAddress>,
    val subject: String,
    val intro: String,
    val seen: Boolean,
    val hasAttachments: Boolean,
    val size: Long,
    val createdAt: Long
) {
    val displaySubject: String get() = subject.ifBlank { "(no subject)" }
}

data class MailAttachment(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val downloadUrl: String
)

/** Full message body (`GET /messages/{id}`). */
data class MessageDetail(
    val id: String,
    val from: MailAddress,
    val to: List<MailAddress>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String,
    val seen: Boolean,
    val text: String,
    val html: List<String>,
    val hasAttachments: Boolean,
    val attachments: List<MailAttachment>,
    val size: Long,
    val createdAt: Long
) {
    val displaySubject: String get() = subject.ifBlank { "(no subject)" }
    val htmlBody: String? get() = html.firstOrNull { it.isNotBlank() }
}

/** Gmail-style folders. mail.tm only stores an inbox, the rest are tracked locally. */
enum class MailFolder(val label: String) {
    INBOX("Inbox"),
    STARRED("Starred"),
    ALL("All mail"),
    TRASH("Trash")
}

internal fun parseIsoToMillis(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }
    }
}
