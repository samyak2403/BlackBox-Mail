package com.samyak.blackboxmailt.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown for any non 2xx response from api.mail.tm. */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Thin client over the mail.tm REST API (https://docs.mail.tm).
 * No API key is required: create an account, exchange it for a bearer token, then poll messages.
 */
object MailTmApi {

    const val BASE_URL = "https://api.mail.tm"

    private val jsonMediaType = "application/json".toMediaType()
    private val mergePatchMediaType = "application/merge-patch+json".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ---------------------------------------------------------------- domains

    /** `GET /domains` - a domain is required before an account can be created. */
    suspend fun domains(page: Int = 1): List<MailDomain> {
        val body = get("$BASE_URL/domains?page=$page", token = null)
        return members(body).map { it.toDomain() }
    }

    // --------------------------------------------------------------- accounts

    /** `POST /accounts` - creates the temporary mailbox. Does not need a token. */
    suspend fun createAccount(address: String, password: String): MailAccount {
        val payload = JSONObject()
            .put("address", address)
            .put("password", password)
            .toString()
        return JSONObject(post("$BASE_URL/accounts", payload, token = null)).toAccount()
    }

    /** `POST /token` - exchanges credentials for the bearer token used by every other call. */
    suspend fun token(address: String, password: String): TokenResult {
        val payload = JSONObject()
            .put("address", address)
            .put("password", password)
            .toString()
        val json = JSONObject(post("$BASE_URL/token", payload, token = null))
        val token = json.optString("token")
        if (token.isBlank()) throw ApiException(401, "The server did not return a token.")
        return TokenResult(id = json.optString("id"), token = token)
    }

    /** `GET /me` - the account owning the supplied token. */
    suspend fun me(token: String): MailAccount =
        JSONObject(get("$BASE_URL/me", token)).toAccount()

    /** `DELETE /accounts/{id}` - irreversible, the mailbox is gone. */
    suspend fun deleteAccount(token: String, id: String) {
        delete("$BASE_URL/accounts/$id", token)
    }

    // --------------------------------------------------------------- messages

    /** `GET /messages` - up to 30 messages per page, newest first. */
    suspend fun messages(token: String, page: Int = 1): List<MessageSummary> {
        val body = get("$BASE_URL/messages?page=$page", token)
        return members(body).map { it.toMessageSummary() }
    }

    /** `GET /messages/{id}` - full message including html body and attachments. */
    suspend fun message(token: String, id: String): MessageDetail =
        JSONObject(get("$BASE_URL/messages/$id", token)).toMessageDetail()

    /** `PATCH /messages/{id}` - marks the message as read. */
    suspend fun markSeen(token: String, id: String, seen: Boolean = true) {
        val payload = JSONObject().put("seen", seen).toString()
        val request = Request.Builder()
            .url("$BASE_URL/messages/$id")
            .patch(payload.toRequestBody(mergePatchMediaType))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()
        execute(request)
    }

    /** `DELETE /messages/{id}` - removes the message from the server for good. */
    suspend fun deleteMessage(token: String, id: String) {
        delete("$BASE_URL/messages/$id", token)
    }

    /** Downloads an attachment (or the raw source) as bytes. */
    suspend fun download(token: String, path: String): ByteArray = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else BASE_URL + path
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes()
            if (!response.isSuccessful || bytes == null) {
                throw ApiException(response.code, errorMessage(response.code, null))
            }
            bytes
        }
    }

    // ----------------------------------------------------------------- plumbing

    private suspend fun get(url: String, token: String?): String = execute(
        Request.Builder().url(url).get().applyDefaults(token).build()
    )

    private suspend fun post(url: String, payload: String, token: String?): String = execute(
        Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .applyDefaults(token)
            .build()
    )

    private suspend fun delete(url: String, token: String?): String = execute(
        Request.Builder().url(url).delete().applyDefaults(token).build()
    )

    private fun Request.Builder.applyDefaults(token: String?): Request.Builder = apply {
        header("Accept", "application/json")
        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(0, e.message ?: "No internet connection.")
        }
        response.use {
            val raw = it.body?.safeString().orEmpty()
            if (!it.isSuccessful) throw ApiException(it.code, errorMessage(it.code, raw))
            raw
        }
    }

    private fun ResponseBody.safeString(): String = try {
        string()
    } catch (_: IOException) {
        ""
    }

    private fun errorMessage(code: Int, raw: String?): String {
        val fromBody = raw?.takeIf { it.trim().startsWith("{") }?.let {
            try {
                val json = JSONObject(it)
                sequenceOf("detail", "hydra:description", "message", "title")
                    .mapNotNull { key -> json.optString(key).takeIf { v -> v.isNotBlank() } }
                    .firstOrNull()
            } catch (_: JSONException) {
                null
            }
        }
        if (!fromBody.isNullOrBlank()) return fromBody
        return when (code) {
            0 -> "No internet connection."
            400 -> "Bad request - something in the payload is missing."
            401 -> "Session expired. Signing back in..."
            404 -> "That mailbox or message no longer exists."
            405 -> "Method not allowed for this endpoint."
            418 -> "The server is a teapot."
            422 -> "The address or password was rejected by the server."
            429 -> "Too many requests - mail.tm allows 8 per second. Retrying shortly."
            in 500..599 -> "mail.tm is having trouble right now. Try again in a moment."
            else -> "Request failed with code $code."
        }
    }

    /** api.mail.tm answers with Hydra collections, but can also answer with a bare array. */
    private fun members(raw: String): List<JSONObject> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                root.optJSONArray("hydra:member")
                    ?: root.optJSONArray("member")
                    ?: JSONArray()
            }
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ mapping

    private fun JSONObject.toDomain() = MailDomain(
        id = optString("id"),
        domain = optString("domain"),
        isActive = optBoolean("isActive", true),
        isPrivate = optBoolean("isPrivate", false)
    )

    private fun JSONObject.toAccount() = MailAccount(
        id = optString("id"),
        address = optString("address"),
        quota = optLong("quota"),
        used = optLong("used"),
        createdAt = parseIsoToMillis(optString("createdAt"))
    )

    private fun JSONObject.toMailAddress(): MailAddress = MailAddress(
        name = optString("name").orEmptyLiteral(),
        address = optString("address").orEmptyLiteral()
    )

    private fun JSONObject.addressList(key: String): List<MailAddress> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.toMailAddress()
                ?: array.optString(index).takeIf { it.isNotBlank() }?.let { MailAddress("", it) }
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.optString("address")?.takeIf { it.isNotBlank() }
                ?: array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun JSONObject.toMessageSummary() = MessageSummary(
        id = optString("id"),
        accountId = optString("accountId"),
        from = optJSONObject("from")?.toMailAddress() ?: MailAddress("", ""),
        to = addressList("to"),
        subject = optString("subject").orEmptyLiteral(),
        intro = optString("intro").orEmptyLiteral(),
        seen = optBoolean("seen", false),
        hasAttachments = optBoolean("hasAttachments", false),
        size = optLong("size"),
        createdAt = parseIsoToMillis(optString("createdAt"))
    )

    private fun JSONObject.toMessageDetail() = MessageDetail(
        id = optString("id"),
        from = optJSONObject("from")?.toMailAddress() ?: MailAddress("", ""),
        to = addressList("to"),
        cc = stringList("cc"),
        bcc = stringList("bcc"),
        subject = optString("subject").orEmptyLiteral(),
        seen = optBoolean("seen", false),
        text = optString("text").orEmptyLiteral(),
        html = stringList("html"),
        hasAttachments = optBoolean("hasAttachments", false),
        attachments = optJSONArray("attachments")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toAttachment() }
        } ?: emptyList(),
        size = optLong("size"),
        createdAt = parseIsoToMillis(optString("createdAt"))
    )

    private fun JSONObject.toAttachment() = MailAttachment(
        id = optString("id"),
        filename = optString("filename").orEmptyLiteral().ifBlank { "attachment" },
        contentType = optString("contentType").orEmptyLiteral()
            .ifBlank { "application/octet-stream" },
        size = optLong("size"),
        downloadUrl = optString("downloadUrl").orEmptyLiteral()
    )

    /** `JSONObject.optString` returns the literal "null" for JSON nulls in some cases. */
    private fun String?.orEmptyLiteral(): String =
        if (this == null || this == "null") "" else this
}
