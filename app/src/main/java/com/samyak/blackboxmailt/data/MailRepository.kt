package com.samyak.blackboxmailt.data

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** An authenticated mail.tm session: the bearer token plus the account it belongs to. */
data class MailSession(val token: String, val account: MailAccount)

/**
 * Owns the mail.tm session lifecycle: creates a mailbox on first launch, re-authenticates
 * when the bearer token expires, and exposes the message operations the UI needs.
 */
class MailRepository(context: Context) {

    val store = SessionStore(context)

    private val sessionLock = Mutex()
    private var cached: MailSession? = null
    private var cachedDomains: List<MailDomain> = emptyList()

    /** Returns a usable session, creating a brand new mailbox when nothing is stored yet. */
    suspend fun session(): MailSession = sessionLock.withLock { obtainSession() }

    /** Throws away the mailbox in use and provisions a fresh random address. */
    suspend fun newAddress(localPart: String? = null, domain: String? = null): MailSession =
        sessionLock.withLock { provision(localPart, domain) }

    /** Switches to another mailbox the user created earlier. */
    suspend fun switchTo(address: String): MailSession = sessionLock.withLock {
        store.activeAddress = address
        store.token = null
        cached = null
        obtainSession()
    }

    /** Deletes the mailbox on the server and falls back to another one (or a new one). */
    suspend fun deleteCurrentAccount() = sessionLock.withLock {
        val current = cached ?: runCatching { obtainSession() }.getOrNull()
        if (current != null) {
            runCatching { retrying { MailTmApi.deleteAccount(current.token, current.account.id) } }
            store.clearFlags(current.account.address)
            store.removeAccount(current.account.address)
        }
        cached = null
        obtainSession()
    }

    suspend fun forgetAccount(address: String) = sessionLock.withLock {
        store.clearFlags(address)
        store.removeAccount(address)
        if (cached?.account?.address == address) cached = null
    }

    fun savedAccounts(): List<StoredAccount> = store.accounts()

    /** Domains available for a custom address. */
    suspend fun domains(): List<MailDomain> {
        cachedDomains.takeIf { it.isNotEmpty() }?.let { return it }
        val fetched = retrying { MailTmApi.domains() }.filter { it.isActive && !it.isPrivate }
        if (fetched.isNotEmpty()) cachedDomains = fetched
        return fetched
    }

    // -------------------------------------------------------------- operations

    suspend fun messages(): List<MessageSummary> =
        authorized { token -> MailTmApi.messages(token) }.sortedByDescending { it.createdAt }

    suspend fun message(id: String): MessageDetail =
        authorized { token -> MailTmApi.message(token, id) }

    suspend fun markSeen(id: String, seen: Boolean = true) {
        authorized { token -> MailTmApi.markSeen(token, id, seen) }
    }

    suspend fun deleteMessage(id: String) {
        authorized { token -> MailTmApi.deleteMessage(token, id) }
    }

    suspend fun downloadAttachment(attachment: MailAttachment): ByteArray =
        authorized { token -> MailTmApi.download(token, attachment.downloadUrl) }

    // ---------------------------------------------------------------- internals

    /** Runs [block] with a valid token, re-authenticating once if the token was rejected. */
    private suspend fun <T> authorized(block: suspend (String) -> T): T {
        val token = session().token
        return try {
            retrying { block(token) }
        } catch (e: ApiException) {
            if (e.code != 401) throw e
            val refreshed = sessionLock.withLock {
                cached = null
                store.token = null
                obtainSession()
            }
            retrying { block(refreshed.token) }
        }
    }

    /** mail.tm allows 8 requests per second; back off instead of surfacing 429s. */
    private suspend fun <T> retrying(attempts: Int = 3, block: suspend () -> T): T {
        var lastError: ApiException? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: ApiException) {
                lastError = e
                val recoverable = e.code == 429 || e.code == 0 || e.code >= 500
                if (!recoverable || attempt == attempts - 1) throw e
                delay(600L * (attempt + 1))
            }
        }
        throw lastError ?: ApiException(0, "Request failed.")
    }

    /** Must be called while holding [sessionLock]. */
    private suspend fun obtainSession(): MailSession {
        cached?.let { return it }

        val stored = store.activeAccount()
        if (stored != null) {
            val cachedToken = store.token
            if (!cachedToken.isNullOrBlank()) {
                val account = runCatching { retrying { MailTmApi.me(cachedToken) } }.getOrNull()
                if (account != null) {
                    return MailSession(cachedToken, account).also { cached = it }
                }
            }
            val reAuthed = runCatching { authenticate(stored.address, stored.password) }.getOrNull()
            if (reAuthed != null) return reAuthed
            // Credentials no longer work (mailboxes expire) - drop them and start over.
            store.removeAccount(stored.address)
        }
        return provision(null, null)
    }

    private suspend fun authenticate(address: String, password: String): MailSession {
        val result = retrying { MailTmApi.token(address, password) }
        val account = runCatching { retrying { MailTmApi.me(result.token) } }
            .getOrElse { MailAccount(id = result.id, address = address) }
        store.token = result.token
        store.activeAddress = address
        return MailSession(result.token, account).also { cached = it }
    }

    /** Creates a new mailbox. Retries with a different local part if the address is taken. */
    private suspend fun provision(localPart: String?, domainName: String?): MailSession {
        val domain = domainName?.takeIf { it.isNotBlank() }
            ?: domains().firstOrNull()?.domain
            ?: throw ApiException(0, "No mail.tm domain is available right now.")

        val password = randomPassword()
        var lastError: ApiException? = null

        repeat(if (localPart == null) 5 else 1) { attempt ->
            val candidate = localPart?.lowercase()?.trim() ?: randomLocalPart()
            val address = "$candidate@$domain"
            try {
                val account = retrying { MailTmApi.createAccount(address, password) }
                store.saveAccount(
                    StoredAccount(
                        id = account.id,
                        address = account.address.ifBlank { address },
                        password = password,
                        createdAt = System.currentTimeMillis()
                    )
                )
                cached = null
                // Small pause: the account needs a beat before /token accepts it.
                if (attempt > 0) delay(300)
                return authenticate(account.address.ifBlank { address }, password)
            } catch (e: ApiException) {
                lastError = e
                // 422 = address already used or rejected; try another one.
                if (e.code != 422 && e.code != 400) throw e
            }
        }
        throw lastError ?: ApiException(0, "Could not create a mailbox.")
    }

    private fun randomLocalPart(): String {
        val first = LETTERS.random(random)
        val rest = (1..9).map { ALPHANUMERIC.random(random) }.joinToString("")
        return "$first$rest"
    }

    private fun randomPassword(): String =
        (1..18).map { PASSWORD_CHARS.random(random) }.joinToString("")

    private companion object {
        val random = Random(System.nanoTime())
        const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
        const val ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789"
        const val PASSWORD_CHARS =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#\$%"
    }
}
