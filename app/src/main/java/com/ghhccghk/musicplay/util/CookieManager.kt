package com.ghhccghk.musicplay.util

import android.content.SharedPreferences
import android.util.Log
import com.ghhccghk.musicplay.MainActivity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cookie

/**
 * 管理 OkHttp Cookies 的持久化存储和加载
 * 用于保存 HTTP 响应中的 Set-Cookie 头，并在后续请求中自动加载
 */
object CookieManager {
    private const val PREFS_NAME = "cookie_prefs"
    private const val COOKIE_KEY = "cookies"
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Cookie 的可序列化数据类
     */
    data class CookieData(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    )

    private fun getSharedPreferences(): SharedPreferences {
        return MainActivity.lontext.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
    }

    /**
     * 保存 Cookies 到 SharedPreferences（持久化存储）
     * @param cookies 要保存的 Cookie 列表
     */
    fun saveCookies(cookies: List<Cookie>) {
        try {
            val cookieDataList = cookies.map { cookie ->
                CookieData(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly
                )
            }

            val listType = Types.newParameterizedType(List::class.java, CookieData::class.java)
            val adapter = moshi.adapter<List<CookieData>>(listType)
            val json = adapter.toJson(cookieDataList)

            getSharedPreferences().edit().apply {
                putString(COOKIE_KEY, json)
                apply()
            }
            Log.d("CookieManager", "Successfully saved ${cookieDataList.size} cookies")
        } catch (e: Exception) {
            Log.e("CookieManager", "Failed to save cookies", e)
            throw e
        }
    }

    /**
     * 从 SharedPreferences 加载 Cookies
     * @return 加载的 Cookie 列表
     */
    fun loadCookies(): List<Cookie> {
        return try {
            val json = getSharedPreferences().getString(COOKIE_KEY, null)
            if (json.isNullOrEmpty()) {
                Log.d("CookieManager", "No saved cookies found")
                return emptyList()
            }

            val listType = Types.newParameterizedType(List::class.java, CookieData::class.java)
            val adapter = moshi.adapter<List<CookieData>>(listType)
            val cookieDataList = adapter.fromJson(json) ?: emptyList()

            val cookies = cookieDataList.mapNotNull { data ->
                try {
                    // 过滤已过期的 cookies
                    if (data.expiresAt < System.currentTimeMillis()) {
                        Log.d("CookieManager", "Skipping expired cookie: ${data.name}")
                        return@mapNotNull null
                    }

                    // 创建 Cookie 对象
                    val builder = Cookie.Builder()
                        .name(data.name)
                        .value(data.value)
                        .domain(data.domain)
                        .path(data.path)
                        .expiresAt(data.expiresAt)

                    // 设置 secure 和 httpOnly 标记
                    if (data.secure) builder.secure()
                    if (data.httpOnly) builder.httpOnly()

                    builder.build()
                } catch (e: Exception) {
                    Log.w("CookieManager", "Failed to recreate cookie: ${data.name}", e)
                    null
                }
            }

            Log.d("CookieManager", "Successfully loaded ${cookies.size} valid cookies")
            cookies
        } catch (e: Exception) {
            Log.e("CookieManager", "Failed to load cookies", e)
            emptyList()
        }
    }

    /**
     * 清除所有保存的 Cookies
     */
    fun clearCookies() {
        try {
            getSharedPreferences().edit().apply {
                remove(COOKIE_KEY)
                apply()
            }
            Log.d("CookieManager", "Cleared all cookies")
        } catch (e: Exception) {
            Log.e("CookieManager", "Failed to clear cookies", e)
        }
    }

    /**
     * 根据 Cookie 名称删除特定的 Cookie
     */
    fun deleteCookie(cookieName: String) {
        try {
            val json = getSharedPreferences().getString(COOKIE_KEY, null) ?: return
            val listType = Types.newParameterizedType(List::class.java, CookieData::class.java)
            val adapter = moshi.adapter<List<CookieData>>(listType)
            val cookieDataList = adapter.fromJson(json)?.toMutableList() ?: mutableListOf()

            // 移除指定名称的 cookie
            cookieDataList.removeAll { it.name == cookieName }

            // 保存更新后的列表
            val updatedJson = adapter.toJson(cookieDataList)
            getSharedPreferences().edit().apply {
                putString(COOKIE_KEY, updatedJson)
                apply()
            }
            Log.d("CookieManager", "Deleted cookie: $cookieName")
        } catch (e: Exception) {
            Log.e("CookieManager", "Failed to delete cookie", e)
        }
    }

    /**
     * 获取所有已保存的 Cookie 信息（用于调试）
     */
    fun getAllCookies(): List<CookieData> {
        return try {
            val json = getSharedPreferences().getString(COOKIE_KEY, null) ?: return emptyList()
            val listType = Types.newParameterizedType(List::class.java, CookieData::class.java)
            val adapter = moshi.adapter<List<CookieData>>(listType)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e("CookieManager", "Failed to get all cookies", e)
            emptyList()
        }
    }
}

