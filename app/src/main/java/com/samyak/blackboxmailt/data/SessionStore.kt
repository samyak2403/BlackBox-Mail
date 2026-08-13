package com.samyak.blackboxmailt.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Local persistence for the mailbox credentials, the cached bearer token and the
 * Gmail-like flags (starred / archived / trashed) that mail.tm itself does not store.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------- credentials

    var activeAddress: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    fun accounts(): List<StoredAccount> = readArray(KEY_ACCOUNTS).mapNotNull { json ->
        val address = json.optString("address")
        val password = json.optString("password")
        if (address.isBlank() || password.isBlank()) return@mapNotNull null
        StoredAccount(
            id = json.optString("id"),
            address = address,
            password = password,
            createdAt = json.optLong("createdAt")
        )
    }.sortedByDescending { it.createdAt }

    fun activeAccount(): StoredAccount? {
        val accounts = accounts()
        val active = activeAddress
        return accounts.firstOrNull { it.address == active } ?: accounts.firstOrNull()
    }

    fun saveAccount(account: StoredAccount, makeActive: Boolean = true) {
        val updated = accounts().filterNot { it.address == account.address } + account
        writeArray(
            KEY_ACCOUNTS,
            updated.map {
                JSONObject()
                    .put("id", it.id)
                    .put("address", it.address)
                    .put("password", it.password)
                    .put("createdAt", it.createdAt)
            }
        )
        if (makeActive) {
            activeAddress = account.address
            token = null
        }
    }

    fun removeAccount(address: String) {
        val remaining = accounts().filterNot { it.address == address }
        writeArray(
            KEY_ACCOUNTS,
            remaining.map {
                JSONObject()
                    .put("id", it.id)
                    .put("address", it.address)
                    .put("password", it.password)
                    .put("createdAt", it.createdAt)
            }
        )
        if (activeAddress == address) {
            activeAddress = remaining.firstOrNull()?.address
            token = null
        }
    }

    // ------------------------------------------------------------------- flags

    fun starred(address: String): Set<String> = readStringSet(flagKey(KEY_STARRED, address))
    fun archived(address: String): Set<String> = readStringSet(flagKey(KEY_ARCHIVED, address))
    fun trashed(address: String): Set<String> = readStringSet(flagKey(KEY_TRASHED, address))

    fun setStarred(address: String, ids: Set<String>) =
        writeStringSet(flagKey(KEY_STARRED, address), ids)

    fun setArchived(address: String, ids: Set<String>) =
        writeStringSet(flagKey(KEY_ARCHIVED, address), ids)

    fun setTrashed(address: String, ids: Set<String>) =
        writeStringSet(flagKey(KEY_TRASHED, address), ids)

    fun clearFlags(address: String) {
        prefs.edit()
            .remove(flagKey(KEY_STARRED, address))
            .remove(flagKey(KEY_ARCHIVED, address))
            .remove(flagKey(KEY_TRASHED, address))
            .remove(flagKey(KEY_NOTIFIED, address))
            .apply()
    }

    // --------------------------------------------------- new mail bookkeeping

    /**
     * Message ids already surfaced to the user. Shared by the in-app poll and the background
     * worker so a message is never announced twice.
     */
    fun notifiedIds(address: String): Set<String> = readStringSet(flagKey(KEY_NOTIFIED, address))

    fun setNotifiedIds(address: String, ids: Set<String>) {
        // Keep the set bounded; mailboxes are disposable and only recent ids matter.
        val bounded = if (ids.size <= MAX_NOTIFIED) ids else ids.toList().takeLast(MAX_NOTIFIED).toSet()
        writeStringSet(flagKey(KEY_NOTIFIED, address), bounded)
    }

    /** True the first time a mailbox is inspected, so existing mail is not announced in bulk. */
    fun hasNotificationBaseline(address: String): Boolean =
        prefs.contains(flagKey(KEY_NOTIFIED, address))

    // --------------------------------------------------------------- internals

    private fun flagKey(prefix: String, address: String) = "$prefix:$address"

    private fun readStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun writeStringSet(key: String, values: Set<String>) =
        prefs.edit().putStringSet(key, values).apply()

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun writeArray(key: String, values: List<JSONObject>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "blackbox_mail_session"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_ACTIVE = "active_address"
        const val KEY_TOKEN = "token"
        const val KEY_STARRED = "starred"
        const val KEY_ARCHIVED = "archived"
        const val KEY_TRASHED = "trashed"
        const val KEY_NOTIFIED = "notified"
        const val MAX_NOTIFIED = 300
    }
}
