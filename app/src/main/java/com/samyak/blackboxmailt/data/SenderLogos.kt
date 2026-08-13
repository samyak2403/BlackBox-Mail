package com.samyak.blackboxmailt.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves the brand icon for a sender so the inbox can show the real logo instead of a letter,
 * the way Gmail does.
 *
 * Icons come from Google's public favicon endpoint, which answers with a decodable PNG and a plain
 * 404 for domains it does not know - that 404 is what makes the letter avatar fallback clean.
 * DuckDuckGo's equivalent endpoint was ruled out: it serves real .ico files, which
 * [BitmapFactory] cannot decode.
 */
object SenderLogos {

    private const val ENDPOINT = "https://www.google.com/s2/favicons"
    private const val REQUESTED_SIZE = 128
    private const val CACHE_DIR = "sender-logos"
    private const val MEMORY_ENTRIES = 120

    /** Public suffixes where the registrable domain needs three labels, not two. */
    private val multiPartSuffixes = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "co.in", "com.au", "net.au", "org.au",
        "co.jp", "com.br", "com.mx", "co.za", "com.tr", "com.cn", "co.kr", "com.sg",
        "com.hk", "co.nz", "com.ar", "com.tw", "co.th", "com.my"
    )

    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)
    private val missing: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val locks = ConcurrentHashMap<String, Mutex>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Already-decoded icon, if any. Lets a row paint its logo without a frame of flicker. */
    fun cached(emailAddress: String): Bitmap? {
        val host = hostOf(emailAddress) ?: return null
        return memory.get(host)
    }

    /** Fetches (or reuses) the icon for the sender's domain. Returns null when there isn't one. */
    suspend fun load(context: Context, emailAddress: String): Bitmap? {
        val host = hostOf(emailAddress) ?: return null
        memory.get(host)?.let { return it }
        if (host in missing) return null

        val mutex = locks.getOrPut(host) { Mutex() }
        return mutex.withLock {
            memory.get(host) ?: when {
                host in missing -> null
                else -> resolve(context.applicationContext, host)
            }
        }
    }

    // ---------------------------------------------------------------- internals

    private suspend fun resolve(context: Context, host: String): Bitmap? =
        withContext(Dispatchers.IO) {
            readFromDisk(context, host)?.let { cached ->
                memory.put(host, cached)
                return@withContext cached
            }

            for (candidate in candidates(host)) {
                val bitmap = download("$ENDPOINT?domain=$candidate&sz=$REQUESTED_SIZE")
                if (bitmap != null) {
                    memory.put(host, bitmap)
                    writeToDisk(context, host, bitmap)
                    return@withContext bitmap
                }
            }

            missing.add(host)
            null
        }

    private fun download(url: String): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "image/png,image/*")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /**
     * `info@account.netflix.com` should still find the Netflix logo, so the full host is tried
     * first and the registrable domain second.
     */
    private fun candidates(host: String): List<String> {
        val registrable = registrableDomain(host)
        return if (registrable == host) listOf(host) else listOf(host, registrable)
    }

    private fun registrableDomain(host: String): String {
        val labels = host.split('.').filter { it.isNotBlank() }
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val take = if (lastTwo in multiPartSuffixes) 3 else 2
        return labels.takeLast(take.coerceAtMost(labels.size)).joinToString(".")
    }

    private fun hostOf(emailAddress: String): String? {
        val host = emailAddress.substringAfterLast('@', "")
            .trim()
            .trimEnd('.')
            .lowercase()
        // Reject anything that is not a plausible domain, including bare local parts.
        if (host.isEmpty() || !host.contains('.') || host.any { it.isWhitespace() }) return null
        return host
    }

    private fun cacheFile(context: Context, host: String): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        val safe = host.replace(Regex("[^a-z0-9.-]"), "_")
        return File(dir, "$safe.png")
    }

    private fun readFromDisk(context: Context, host: String): Bitmap? {
        val file = cacheFile(context, host)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToDisk(context: Context, host: String, bitmap: Bitmap) {
        try {
            val file = cacheFile(context, host)
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: IOException) {
            // A missing disk cache only costs one extra request later.
        }
    }
}
