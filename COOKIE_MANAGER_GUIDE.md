## Cookie 管理说明

### 概述

`CookieManager` 是一个用于处理 OkHttp Cookies 持久化的工具类。它能够自动保存服务器返回的
Cookies，并在后续请求中自动加载这些 Cookies。

### 工作流程

#### 1. 自动保存 Cookie

当 HTTP 响应返回时，`KugouAPi` 中的 `CookieJar` 会自动：

- 截获服务器返回的 Set-Cookie 头
- 调用 `CookieManager.saveCookies()` 将 Cookies 保存到 SharedPreferences
- 这一切都是自动进行的，无需手动干预

```kotlin
override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    cookieStore[url] = cookies
    // 自动保存到持久化存储
    if (cookies.isNotEmpty()) {
        try {
            CookieManager.saveCookies(cookies)
            Log.d("KugouAPi", "Saved ${cookies.size} cookies to persistent storage")
        } catch (e: Exception) {
            Log.w("KugouAPi", "Failed to save cookies to persistent storage", e)
        }
    }
}
```

#### 2. 自动加载 Cookie

当发送 HTTP 请求时，`CookieJar` 会自动：

- 调用 `CookieManager.loadCookies(url)` 从持久化存储加载之前保存的 Cookies
- 自动过滤已过期的 Cookies
- 这些 Cookies 会自动添加到请求的 Cookie 头中

```kotlin
override fun loadForRequest(url: HttpUrl): List<Cookie> {
    // 自动加载之前保存的 cookies
    val savedCookies = try {
        CookieManager.loadCookies(url)
    } catch (e: Exception) {
        Log.w("KugouAPi", "Failed to load cookies from persistent storage", e)
        emptyList()
    }

    val cookies = mutableListOf<Cookie>()
    cookies.addAll(savedCookies)
    // ... 添加其他 cookies
    return cookies
}
```

### 使用示例

#### 场景 1：首次登录

```kotlin
// 第一次调用登录接口
val result = KugouAPi.loginCellphone(mobile, code)
// 服务器返回的 cookies 会自动保存

// 第二次调用其他接口
val userData = KugouAPi.getUserDetail()
// 之前保存的 cookies 会自动加载并用于请求
```

#### 场景 2：App 重启后仍保持登录状态

```kotlin
// App 首次启动
// 用户登录，获得 cookies，自动保存

// App 重启后
// 无需重新登录，之前的 cookies 仍然有效
val userData = KugouAPi.getUserDetail()
// CookieManager 会自动从 SharedPreferences 加载之前保存的 cookies
```

#### 场景 3：手动清除 Cookies

```kotlin
// 用户点击退出登录
CookieManager.clearCookies()
// 所有保存的 cookies 都被清除

// 清除特定的 Cookie
CookieManager.deleteCookie("token")
```

#### 场景 4：调试 - 获取所有保存的 Cookies

```kotlin
// 查看当前保存的所有 cookies
val allCookies = CookieManager.getAllCookies()
for (cookie in allCookies) {
    Log.d("CookieManager", "Cookie: ${cookie.name}=${cookie.value}, expired=${cookie.expiresAt}")
}
```

### API 文档

#### CookieManager.saveCookies(cookies: List<Cookie>)

- **作用**：将 Cookies 列表保存到 SharedPreferences
- **参数**：cookies - OkHttp Cookie 对象列表
- **异常**：可能抛出 Exception，已在 KugouAPi 中捕获处理
- **自动调用**：当服务器返回 Set-Cookie 头时自动调用

#### CookieManager.loadCookies(url: HttpUrl): List<Cookie>

- **作用**：从 SharedPreferences 加载已保存的 Cookies
- **参数**：url - 请求的 HttpUrl（用于创建 Cookie 对象）
- **返回**：List<Cookie> - 有效的（未过期）Cookie 列表
- **特性**：自动过滤已过期的 Cookies
- **自动调用**：当发送 HTTP 请求时自动调用

#### CookieManager.clearCookies()

- **作用**：清除所有保存的 Cookies
- **用途**：用户退出登录时调用

#### CookieManager.deleteCookie(cookieName: String)

- **作用**：删除指定名称的 Cookie
- **参数**：cookieName - Cookie 名称（如 "token"、"dfid" 等）

#### CookieManager.getAllCookies(): List<CookieData>

- **作用**：获取所有已保存的 Cookie 信息（仅用于调试）
- **返回**：CookieData 列表，包含 Cookie 的所有属性

### Cookie 过期管理

`CookieManager` 会自动处理 Cookie 过期问题：

- 保存时：记录 Cookie 的过期时间
- 加载时：自动过滤已过期的 Cookies
- 不会加载过期的 Cookies 到请求中

### SharedPreferences 存储细节

- **存储位置**：SharedPreferences，名称为 "cookie_prefs"
- **存储格式**：JSON 格式（使用 Moshi 序列化）
- **存储密钥**："cookies"
- **数据持久化**：应用数据目录，重启后仍然存在

### 日志记录

所有操作都有相应的日志记录，便于调试：

- `D/CookieManager`: 保存/加载成功
- `W/CookieManager`: 加载失败或其他警告
- `E/CookieManager`: 严重错误

### 最佳实践

1. **App 启动时**：无需手动初始化，`CookieManager` 在首次使用时自动初始化

2. **用户登录**：
   ```kotlin
   val loginResult = KugouAPi.loginCellphone(mobile, code)
   // 返回的 cookies 会自动保存，无需手动操作
   ```

3. **用户登出**：
   ```kotlin
   CookieManager.clearCookies()
   // 清除所有登录信息
   ```

4. **检查登录状态**：
   ```kotlin
   val savedCookies = CookieManager.getAllCookies()
   val isLoggedIn = savedCookies.any { it.name == "token" }
   ```

### 安全建议

1. **Token 安全**：Cookies 存储在 SharedPreferences 中，建议使用加密的 SharedPreferences
2. **过期时间**：依赖服务器设置的 Cookie 过期时间，客户端会自动过滤
3. **清除数据**：用户退出时使用 `CookieManager.clearCookies()` 清除所有数据

### 兼容性

- 支持所有 OkHttp 3.x 版本
- 支持 Android API 21+
- 使用 Moshi 进行 JSON 序列化，确保跨版本兼容

