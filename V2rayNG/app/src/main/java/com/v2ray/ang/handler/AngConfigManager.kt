package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.HY2
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import java.net.URI
import java.net.UnknownHostException

object AngConfigManager {

    /**
     * Shares the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share config to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares non-custom configurations to the clipboard.
     *
     * @param context The context.
     * @param serverList The list of server GUIDs.
     * @return The number of configurations shared.
     */
    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count() - 1
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share non-custom configs to clipboard", e)
            return -1
        }
    }

    /**
     * Shares the configuration as a QR code.
     *
     * @param guid The GUID of the configuration.
     * @return The QR code bitmap.
     */
    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share config as QR code", e)
            return null
        }
    }

    /**
     * Shares the full content of the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = V2rayConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                val config = MmkvManager.decodeServerConfig(guid)
                if (config?.configType == EConfigType.HYSTERIA2) {
                    val socksPort =
                            Utils.findFreePort(listOf(100 + SettingsManager.getSocksPort(), 0))
                    val hy2Config = Hysteria2Fmt.toNativeConfig(config, socksPort)
                    Utils.setClipboard(
                            context,
                            JsonUtil.toJsonPretty(hy2Config) + "\n" + result.content
                    )
                    return 0
                }
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share full content to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares the configuration.
     *
     * @param guid The GUID of the configuration.
     * @return The configuration string.
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme +
                    when (config.configType) {
                        EConfigType.VMESS -> VmessFmt.toUri(config)
                        EConfigType.CUSTOM -> ""
                        EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                        EConfigType.SOCKS -> SocksFmt.toUri(config)
                        EConfigType.HTTP -> ""
                        EConfigType.VLESS -> VlessFmt.toUri(config)
                        EConfigType.TROJAN -> TrojanFmt.toUri(config)
                        EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                        EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
                    }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share config for GUID: $guid", e)
            return ""
        }
    }

    /**
     * Imports a batch of configurations.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return A pair containing the number of configurations and subscriptions imported.
     */
    fun importBatchConfig(server: String?, subid: String, append: Boolean): Pair<Int, Int> {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid)
        }

        var countSub = parseBatchSubscription(server)
        if (countSub <= 0) {
            countSub = parseBatchSubscription(Utils.decode(server))
        }
        if (countSub > 0) {
            updateConfigViaSubAll()
        }

        return count to countSub
    }

    /**
     * Parses a batch of subscriptions.
     *
     * @param servers The servers string.
     * @return The number of subscriptions parsed.
     */
    private fun parseBatchSubscription(servers: String?): Int {
        try {
            if (servers == null) {
                return 0
            }

            var count = 0
            servers.lines().distinct().forEach { str ->
                if (Utils.isValidSubUrl(str)) {
                    count += importUrlAsSubscription(str)
                }
            }
            return count
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse batch subscription", e)
        }
        return 0
    }

    /**
     * Parses a batch of configurations.
     *
     * @param servers The servers string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            if (servers == null) {
                Log.d(AppConfig.TAG, "parseBatchConfig: servers is null")
                return 0
            }
            
            val lines = servers.lines().filter { it.isNotBlank() }
            Log.d(AppConfig.TAG, "parseBatchConfig: Found ${lines.size} non-empty lines to parse")
            
            val removedSelectedServer =
                    if (!TextUtils.isEmpty(subid) && !append) {
                        MmkvManager.decodeServerConfig(MmkvManager.getSelectServer().orEmpty())
                                ?.let {
                                    if (it.subscriptionId == subid) {
                                        return@let it
                                    }
                                    return@let null
                                }
                    } else {
                        null
                    }
            if (!append) {
                MmkvManager.removeServerViaSubid(subid)
            }

            val subItem = MmkvManager.decodeSubscription(subid)
            var count = 0
            servers.lines().distinct().reversed().forEach {
                if (it.isNotBlank()) {
                    val resId = parseConfig(it, subid, subItem, removedSelectedServer)
                    if (resId == 0) {
                        count++
                    } else {
                        Log.d(AppConfig.TAG, "parseBatchConfig: Failed to parse line (resId=$resId): ${it.take(50)}...")
                    }
                }
            }
            Log.i(AppConfig.TAG, "parseBatchConfig: Successfully parsed $count configs out of ${lines.size} lines")
            return count
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse batch config", e)
        }
        return 0
    }

    /**
     * Parses a custom configuration server.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @return The number of configurations parsed.
     */
    private fun parseCustomConfigServer(server: String?, subid: String): Int {
        if (server == null) {
            return 0
        }
        if (server.contains("inbounds") &&
                        server.contains("outbounds") &&
                        server.contains("routing")
        ) {
            try {
                val serverList: Array<Any> = JsonUtil.fromJson(server, Array<Any>::class.java)

                if (serverList.isNotEmpty()) {
                    var count = 0
                    for (srv in serverList.reversed()) {
                        val config = CustomFmt.parse(JsonUtil.toJson(srv)) ?: continue
                        config.subscriptionId = subid
                        val key = MmkvManager.encodeServerConfig("", config)
                        MmkvManager.encodeServerRaw(key, JsonUtil.toJsonPretty(srv) ?: "")
                        count += 1
                    }
                    return count
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                // For compatibility
                val config = CustomFmt.parse(server) ?: return 0
                config.subscriptionId = subid
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to parse custom config server as single config", e)
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config =
                        WireguardFmt.parseWireguardConfFile(server)
                                ?: return R.string.toast_incorrect_protocol
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to parse WireGuard config file", e)
            }
            return 0
        } else {
            return 0
        }
    }

    /**
     * Parses the configuration from a QR code or string.
     *
     * @param str The configuration string.
     * @param subid The subscription ID.
     * @param subItem The subscription item.
     * @param removedSelectedServer The removed selected server.
     * @return The result code.
     */
    private fun parseConfig(
            str: String?,
            subid: String,
            subItem: SubscriptionItem?,
            removedSelectedServer: ProfileItem?
    ): Int {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return R.string.toast_none_data
            }

            val config =
                    if (str.startsWith(EConfigType.VMESS.protocolScheme)) {
                        VmessFmt.parse(str)
                    } else if (str.startsWith(EConfigType.SHADOWSOCKS.protocolScheme)) {
                        ShadowsocksFmt.parse(str)
                    } else if (str.startsWith(EConfigType.SOCKS.protocolScheme)) {
                        SocksFmt.parse(str)
                    } else if (str.startsWith(EConfigType.TROJAN.protocolScheme)) {
                        TrojanFmt.parse(str)
                    } else if (str.startsWith(EConfigType.VLESS.protocolScheme)) {
                        VlessFmt.parse(str)
                    } else if (str.startsWith(EConfigType.WIREGUARD.protocolScheme)) {
                        WireguardFmt.parse(str)
                    } else if (str.startsWith(EConfigType.HYSTERIA2.protocolScheme) ||
                                    str.startsWith(HY2)
                    ) {
                        Hysteria2Fmt.parse(str)
                    } else {
                        null
                    }

            if (config == null) {
                return R.string.toast_incorrect_protocol
            }
            // filter
            if (subItem?.filter != null &&
                            subItem.filter?.isNotEmpty() == true &&
                            config.remarks.isNotEmpty()
            ) {
                val matched =
                        Regex(pattern = subItem.filter ?: "")
                                .containsMatchIn(input = config.remarks)
                if (!matched) return -1
            }

            config.subscriptionId = subid
            val guid = MmkvManager.encodeServerConfig("", config)
            if (removedSelectedServer != null &&
                            config.server == removedSelectedServer.server &&
                            config.serverPort == removedSelectedServer.serverPort
            ) {
                MmkvManager.setSelectServer(guid)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse config", e)
            return -1
        }
        return 0
    }

    /**
     * Updates the configuration via all subscriptions.
     *
     * @return The number of configurations updated.
     */
    fun updateConfigViaSubAll(): Int {
        var count = 0
        try {
            MmkvManager.decodeSubscriptions().forEach { count += updateConfigViaSub(it) }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to update config via all subscriptions", e)
            return 0
        }
        return count
    }

    /**
     * Automatically fetches subscription data using the authenticated user's credentials. This
     * method uses the subscription ID from AuthManager to fetch configs.
     *
     * @return The number of configurations imported.
     */
    fun autoFetchSubscriptionWithAuth(): Int {
        try {
            // Check if user is logged in
            if (!AuthManager.isLoggedIn()) {
                Log.w(AppConfig.TAG, "Auto-fetch subscription: User not logged in")
                return 0
            }

            // Get subscription URL from AuthManager
            val url = AuthManager.getSubscriptionUrl()
            if (url == null) {
                Log.e(AppConfig.TAG, "Auto-fetch subscription: Unable to build subscription URL")
                return 0
            }

            Log.i(AppConfig.TAG, "Auto-fetch subscription from: $url")

            // Use the subscription ID as the subid for parsing
            val subId = AuthManager.getSubscriptionId() ?: "auth_sub"
            Log.i(AppConfig.TAG, "Auto-fetch subscription: subId=$subId")

            // Fetch subscription data with headers
            var configText = ""
            var userInfoHeader: String? = null
            
            // Only try to use proxy if VPN service is running
            val httpPort = if (V2RayServiceManager.isRunning()) {
                SettingsManager.getHttpPort()
            } else {
                0  // Skip proxy if service is not running
            }
            
            if (httpPort > 0) {
                // Try with proxy first if service is running
                try {
                    val (content, userInfo) = HttpUtil.getUrlContentWithUserAgentAndHeaders(
                            url,
                            AppConfig.CUSTOM_API_USER_AGENT,
                            AppConfig.CUSTOM_API_TIMEOUT_MS,
                            httpPort,
                            useApiAuth = true  // Enable API authentication
                    )
                    configText = content
                    userInfoHeader = userInfo
                } catch (e: Exception) {
                    if (e is java.net.ConnectException && e.message?.contains("127.0.0.1") == true) {
                        // Proxy connection failed (proxy not ready)
                        Log.w(
                                AppConfig.TAG,
                                "Auto-fetch subscription: proxy not ready, will retry without proxy",
                                e
                        )
                    } else {
                        Log.e(
                                AppConfig.TAG,
                                "Auto-fetch subscription: proxy not ready or other error",
                                e
                        )
                    }
                }
            }

            if (configText.isEmpty()) {
                        try {
                    val (content, userInfo) = HttpUtil.getUrlContentWithUserAgentAndHeaders(
                            url,
                            AppConfig.CUSTOM_API_USER_AGENT,
                            AppConfig.CUSTOM_API_TIMEOUT_MS,
                            0,
                            useApiAuth = true  // Enable API authentication
                    )
                    configText = content
                    userInfoHeader = userInfo
                        } catch (e: Exception) {
                            Log.e(
                                    AppConfig.TAG,
                                    "Auto-fetch subscription: Failed to get URL content",
                                    e
                            )
                }
            }

            // Parse and save subscription user info if present
            if (!userInfoHeader.isNullOrEmpty()) {
                Log.i(AppConfig.TAG, "Subscription-UserInfo: $userInfoHeader")
                parseAndSaveSubscriptionUserInfo(userInfoHeader, subId)
            }

            if (configText.isEmpty()) {
                Log.e(AppConfig.TAG, "Auto-fetch subscription: No data received")
                return 0
            }

            Log.i(AppConfig.TAG, "Auto-fetch subscription: Received ${configText.length} characters of data")
            Log.d(AppConfig.TAG, "Auto-fetch subscription: First 200 chars: ${configText.take(200)}")
            logLargeLogcat("Auto-fetch subscription: Full response", configText)

            // Parse and import configs
            val count = parseConfigViaSub(configText, subId, false)
            Log.i(AppConfig.TAG, "Auto-fetch subscription: Parsed $count configs")
            return count
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to auto-fetch subscription with auth", e)
            return 0
        }
    }

    private fun logLargeLogcat(prefix: String, content: String) {
        if (content.isEmpty()) return
        // Logcat truncates very long lines; split into chunks.
        val chunkSize = 3500
        var i = 0
        var part = 1
        while (i < content.length) {
            val end = (i + chunkSize).coerceAtMost(content.length)
            Log.i(AppConfig.TAG, "$prefix (part $part): ${content.substring(i, end)}")
            i = end
            part++
        }
    }

    /**
     * Updates the configuration via a subscription.
     *
     * @param it The subscription item.
     * @return The number of configurations updated.
     */
    fun updateConfigViaSub(it: Pair<String, SubscriptionItem>): Int {
        try {
            if (TextUtils.isEmpty(it.first) ||
                            TextUtils.isEmpty(it.second.remarks) ||
                            TextUtils.isEmpty(it.second.url)
            ) {
                return 0
            }
            if (!it.second.enabled) {
                return 0
            }
            val url = HttpUtil.toIdnUrl(it.second.url)
            if (!Utils.isValidUrl(url)) {
                return 0
            }
            if (!it.second.allowInsecureUrl) {
                if (!Utils.isValidSubUrl(url)) {
                    return 0
                }
            }
            Log.i(AppConfig.TAG, url)
            val userAgent = it.second.userAgent

            var dnsFailed = false
            var proxyFailed = false
            var connectionFailed = false
            var configText = ""
            
            // Only try to use proxy if VPN service is running
            val httpPort = if (V2RayServiceManager.isRunning()) {
                SettingsManager.getHttpPort()
            } else {
                0  // Skip proxy if service is not running
            }
            
            if (httpPort > 0) {
                // Try with proxy first if service is running
                configText = try {
                    HttpUtil.getUrlContentWithUserAgent(url, userAgent, 15000, httpPort)
                } catch (e: Exception) {
                    if (e is UnknownHostException) {
                        dnsFailed = true
                    } else if (e is java.net.ConnectException && e.message?.contains("127.0.0.1") == true) {
                        // Proxy connection failed (proxy not ready)
                        proxyFailed = true
                        Log.w(
                                AppConfig.ANG_PACKAGE,
                                "Update subscription: proxy not ready, will retry without proxy",
                                e
                        )
                    } else {
                        Log.e(
                                AppConfig.ANG_PACKAGE,
                                "Update subscription: error with proxy",
                                e
                        )
                    }
                    ""
                }
            }
            if (configText.isEmpty()) {
                configText =
                        try {
                            HttpUtil.getUrlContentWithUserAgent(url, userAgent)
                        } catch (e: Exception) {
                            if (e is UnknownHostException) {
                                dnsFailed = true
                            } else if (e is java.net.ProtocolException || 
                                      (e is java.io.IOException && e.message?.contains("prematurely") == true)) {
                                // ProtocolException or premature connection close - treat as network issue
                                connectionFailed = true
                                Log.w(
                                        AppConfig.TAG,
                                        "Update subscription: Connection issue, will try fallback",
                                        e
                                )
                            } else {
                                Log.e(
                                        AppConfig.TAG,
                                        "Update subscription: Failed to get URL content with user agent",
                                        e
                                )
                            }
                            ""
                        }
            }

            // If DNS failed, proxy failed, or connection issue on device, retry once by hitting the pinned IP with the same Host header.
            if (configText.isEmpty() && (dnsFailed || proxyFailed || connectionFailed) && url.contains("dev.s.fastshot.net")) {
                val fallback = tryFetchSubscriptionViaFallbackIp(url, userAgent)
                if (fallback.isNotEmpty()) {
                    configText = fallback
                }
            }

            if (configText.isEmpty()) {
                return 0
            }
            return parseConfigViaSub(configText, it.first, false)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to update config via subscription", e)
            return 0
        }
    }

    private fun tryFetchSubscriptionViaFallbackIp(url: String, userAgent: String?): String {
        return try {
            val uri = URI(Utils.fixIllegalUrl(url))
            val originalHost = uri.host ?: return ""
            val port = uri.port
            if (port <= 0) return ""

            // Only handle our custom backend host.
            if (originalHost != "dev.s.fastshot.net") return ""

            val hostHeader = "$originalHost:$port"
            val ipUrl = URI(
                uri.scheme,
                uri.userInfo,
                AppConfig.CUSTOM_API_FALLBACK_IPV4,
                port,
                uri.rawPath,
                uri.rawQuery,
                uri.rawFragment
            ).toString()

            Log.w(
                AppConfig.TAG,
                "DNS/proxy failed; retrying subscription via IP url=$ipUrl host=$hostHeader"
            )

            val conn = HttpUtil.createProxyConnection(
                ipUrl,
                0,
                AppConfig.CUSTOM_API_TIMEOUT_MS,
                AppConfig.CUSTOM_API_TIMEOUT_MS
            ) ?: return ""

            try {
                val finalUserAgent = userAgent?.takeIf { it.isNotBlank() }
                    ?: AppConfig.CUSTOM_API_USER_AGENT
                conn.setRequestProperty("User-agent", finalUserAgent)
                conn.setRequestProperty("Host", hostHeader)
                
                // Add API authentication headers for custom backend
                if (originalHost == "dev.s.fastshot.net") {
                    val authHeaders = com.v2ray.ang.util.ApiSecurityUtil.createAuthHeaders("GET", ipUrl)
                    authHeaders.forEach { (key, value) ->
                        conn.setRequestProperty(key, value)
                    }
                }
                
                conn.connect()
                
                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    return conn.inputStream.use { it.bufferedReader().readText() }
                } else {
                    // Server returned an error response
                    val errorBody = try {
                        conn.errorStream?.use { it.bufferedReader().readText() } ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    Log.e(
                        AppConfig.TAG,
                        "Fallback IP subscription fetch got HTTP $responseCode: $errorBody"
                    )
                    return ""
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Fallback IP subscription fetch failed", e)
            ""
        }
    }

    /**
     * Parses the configuration via a subscription.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        Log.d(AppConfig.TAG, "parseConfigViaSub: Attempting to decode and parse as batch config")
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            Log.d(AppConfig.TAG, "parseConfigViaSub: Decoding failed, trying raw server string")
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            Log.d(AppConfig.TAG, "parseConfigViaSub: Batch parsing failed, trying custom config")
            count = parseCustomConfigServer(server, subid)
        }
        if (count <= 0) {
            Log.w(AppConfig.TAG, "parseConfigViaSub: All parsing methods failed, no configs imported")
        }
        return count
    }

    /**
     * Imports a URL as a subscription.
     *
     * @param url The URL.
     * @return The number of subscriptions imported.
     */
    private fun importUrlAsSubscription(url: String): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.second.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        MmkvManager.encodeSubscription("", subItem)
        return 1
    }

    /**
     * Creates an intelligent selection configuration based on multiple server configurations.
     *
     * @param context The application context used for configuration generation.
     * @param guidList The list of server GUIDs to be included in the intelligent selection.
     * ```
     *                 Each GUID represents a server configuration that will be combined.
     * @param subid
     * ```
     * The subscription ID to associate with the generated configuration.
     * ```
     *              This helps organize the configuration under a specific subscription.
     * @return
     * ```
     * The GUID key of the newly created intelligent selection configuration,
     * ```
     *         or null if the operation fails (e.g., empty guidList or configuration parsing error).
     * ```
     */
    fun createIntelligentSelection(
            context: Context,
            guidList: List<String>,
            subid: String
    ): String? {
        if (guidList.isEmpty()) {
            return null
        }
        val result = V2rayConfigManager.genV2rayConfig(context, guidList) ?: return null
        val config = CustomFmt.parse(JsonUtil.toJson(result)) ?: return null
        config.subscriptionId = subid
        val key = MmkvManager.encodeServerConfig("", config)
        MmkvManager.encodeServerRaw(key, JsonUtil.toJsonPretty(result) ?: "")
        return key
    }

    /**
     * Parses subscription-userinfo header and saves it to the subscription item.
     * Header format: upload=123; download=456; total=789; expire=1234567890
     *
     * @param userInfoHeader The subscription-userinfo header value
     * @param subscriptionId The subscription ID
     */
    private fun parseAndSaveSubscriptionUserInfo(userInfoHeader: String, subscriptionId: String) {
        try {
            val parts = userInfoHeader.split(";").map { it.trim() }
            var upload: Long? = null
            var download: Long? = null
            var total: Long? = null
            var expire: Long? = null

            for (part in parts) {
                val keyValue = part.split("=", limit = 2)
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()
                    val value = keyValue[1].trim().toLongOrNull()
                    when (key.lowercase()) {
                        "upload" -> upload = value
                        "download" -> download = value
                        "total" -> total = value
                        "expire" -> expire = value
                    }
                }
            }

            // Get existing subscription item and update it
            val subItem = MmkvManager.decodeSubscription(subscriptionId)
            if (subItem != null) {
                subItem.upload = upload
                subItem.download = download
                subItem.total = total
                subItem.expire = expire
                MmkvManager.encodeSubscription(subscriptionId, subItem)
                Log.i(AppConfig.TAG, "Saved subscription userinfo: upload=$upload, download=$download, total=$total, expire=$expire")
            } else {
                Log.w(AppConfig.TAG, "Subscription not found for ID: $subscriptionId")
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse subscription userinfo", e)
        }
    }
}
