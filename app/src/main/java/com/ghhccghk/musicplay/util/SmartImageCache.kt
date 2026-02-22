package com.ghhccghk.musicplay.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SmartImageCache {
    private lateinit var cacheDir: File
    private var maxCacheSize: Long = 50L * 1024 * 1024 // 默认50MB
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Track ongoing downloads so multiple callers asking for the same URL will wait on the same Deferred
    private val inProgress = ConcurrentHashMap<String, CompletableDeferred<Uri?>>()

    /**
     * Initialize the cache directory. Safe to call multiple times.
     * If external files dir is unavailable, falls back to application filesDir.
     */
    fun init(context: Context, dirName: String = "cache/smart_image_cache", maxSize: Long = maxCacheSize) {
        val base = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
        cacheDir = File(base, dirName).apply { if (!exists()) mkDirsSafely() }
        maxCacheSize = maxSize
    }

    private fun File.mkDirsSafely() {
        try {
            mkdirs()
        } catch (t: Throwable) {
            Log.w("SmartImageCache", "Failed to create cache directory: $this", t)
        }
    }

    private fun ensureInitialized() {
        if (!::cacheDir.isInitialized) throw IllegalStateException("SmartImageCache is not initialized. Call SmartImageCache.init(context) before use.")
    }

    fun hasCache(url: String, customHash: String? = null): Boolean {
        ensureInitialized()
        val fileName = (customHash ?: url).md5()
        val file = File(cacheDir, fileName)
        return file.exists() && file.length() > 0L
    }

    fun getCachedUri(url: String, customHash: String? = null): Uri? {
        return try {
            ensureInitialized()
            val fileName = (customHash ?: url).md5()
            val file = File(cacheDir, fileName)
            if (file.exists() && file.length() > 0L) Uri.fromFile(file) else null
        } catch (e: Exception) {
            Log.e("SmartImageCache", "getCachedUri failed", e)
            null
        }
    }

    /**
     * Get a cached Uri or download and cache the resource.
     * Ensures only one download per key (url or customHash) happens at a time.
     */
    suspend fun getOrDownload(url: String, customHash: String? = null): Uri? {
        ensureInitialized()

        if (url.isBlank()) {
            Log.w("SmartImageCache", "无效 URL：'$url'")
            return null
        }

        val key = (customHash ?: url).md5()
        val file = File(cacheDir, key)

        // If valid file exists already, update lastModified and return immediately
        if (file.exists() && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            Log.d("SmartImageCache", "获取到缓存 : $file")
            return Uri.fromFile(file)
        }

        // Try to register our download atomically. If another deferred already present, await it.
        val deferred = CompletableDeferred<Uri?>()
        val prev = inProgress.putIfAbsent(key, deferred)
        if (prev != null) {
            try {
                return prev.await()
            } catch (e: Exception) {
                Log.w("SmartImageCache", "Awaiting existing download for $key failed, will try download itself", e)
                // attempt to replace with our deferred; if replace fails, await current
                if (!inProgress.replace(key, prev, deferred)) {
                    val current = inProgress[key]
                    try {
                        return current?.await()
                    } catch (_: Exception) {
                        // fall through and let this coroutine attempt the download
                    }
                }
            }
        }

        try {
            return withContext(Dispatchers.IO) {
                // Use a temp file and move it into place when finished
                val tmpFile = File(cacheDir, "$key.tmp")
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w("SmartImageCache", "Download failed, HTTP ${response.code} for $url")
                            deferred.complete(null)
                            return@withContext null
                        }

                        // Write to temp file (response.body is non-nullable in newer okHttp Kotlin bindings)
                        val body = response.body
                        body.byteStream().use { input ->
                            // Ensure parent exists
                            if (!cacheDir.exists()) cacheDir.mkdirs()

                            tmpFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // If tmpFile is empty, treat as failure
                        if (!tmpFile.exists() || tmpFile.length() == 0L) {
                            tmpFile.delete()
                            Log.w("SmartImageCache", "Downloaded file empty for $url")
                            deferred.complete(null)
                            return@withContext null
                        }

                        // Atomically move tmp to final
                        try {
                            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        } catch (moveEx: Throwable) {
                            // Fallback to renameTo
                            val renamed = tmpFile.renameTo(file)
                            if (!renamed) {
                                tmpFile.delete()
                                Log.w("SmartImageCache", "Failed to move tmp file into place for $url", moveEx)
                                deferred.complete(null)
                                return@withContext null
                            }
                        }

                        file.setLastModified(System.currentTimeMillis())
                        trimCache()
                        Log.d("SmartImageCache", "request success 获取到缓存 : $file")
                        val result = Uri.fromFile(file)
                        deferred.complete(result)
                        return@withContext result
                    }
                } catch (e: Exception) {
                    Log.e("SmartImageCache", "下载失败: $e url: $url")
                    tmpFile.delete()
                    deferred.complete(null)
                    null
                } finally {
                    // ensure we don't leave tmp files around in common failure cases
                    if (tmpFile.exists() && (file.exists().not())) tmpFile.delete()
                }
            }
        } finally {
            // clean up inProgress map so subsequent attempts can run, only remove if our deferred is still present
            inProgress.remove(key, deferred)
        }
    }

    fun clearAll() {
        if (::cacheDir.isInitialized && cacheDir.exists()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
    }

    private fun trimCache() {
        if (!::cacheDir.isInitialized) return
        val files = cacheDir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxCacheSize) return

        files.sortedBy { it.lastModified() }.forEach { f ->
            val size = f.length()
            try {
                if (f.delete()) {
                    total -= size
                }
            } catch (t: Throwable) {
                Log.w("SmartImageCache", "Failed to delete cache file $f", t)
            }
            if (total <= maxCacheSize) return
        }
    }

    private fun String.md5(): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
