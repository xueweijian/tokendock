package dev.minis.tokendock.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * OpenCode Go 用量 API（未公开文档）。
 * GET https://opencode.ai/zen/go/v1/usage  Bearer 认证。
 * 注意：Cloudflare 会拦截非浏览器 User-Agent，必须带 Chrome UA。
 */
object OpencodeApi {

    private const val ENDPOINT = "https://opencode.ai/zen/go/v1/usage"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    fun fetch(apiKey: String): ProviderSnapshot {
        val body = httpGet(ENDPOINT, apiKey)
        val usage = JSONObject(body).getJSONObject("usage")
        return ProviderSnapshot(
            providerId = "opencode",
            ok = true,
            fetchedAtMillis = System.currentTimeMillis(),
            ocRolling = parseWindow(usage, "rolling"),
            ocWeekly = parseWindow(usage, "weekly"),
            ocMonthly = parseWindow(usage, "monthly"),
        )
    }

    private fun parseWindow(usage: JSONObject, key: String): Progress? {
        val obj = usage.optJSONObject(key) ?: return null
        val resets = obj.optString("resetsAt", "").replace("Z", "+00:00")
        val millis = runCatching { Instant.parse(resets).toEpochMilli() }.getOrNull()
        return Progress.of(obj.opt("percent"), millis)
    }
}

/**
 * 智谱 GLM Coding Plan 监控 API（未公开文档）。
 * GET {base}/api/monitor/usage/quota/limit  Bearer 认证。
 * 字段坑：TIME_LIMIT 里 usage=上限、currentValue=已用、usageDetails[].usage=已用。
 */
object GlmApi {

    private const val BASE_CN = "https://open.bigmodel.cn"

    fun fetch(apiKey: String, global: Boolean = false): ProviderSnapshot {
        val base = if (global) "https://api.z.ai" else BASE_CN
        val body = httpGet("$base/api/monitor/usage/quota/limit", apiKey)
        val root = JSONObject(body)
        if (!root.optBoolean("success", false)) {
            throw IllegalStateException(root.optString("msg", "GLM API 返回 success=false"))
        }
        val limits = root.getJSONObject("data").getJSONArray("limits")
        var tokens5h: Progress? = null
        var mcp: Progress? = null
        var mcpDetails = listOf<Pair<String, Int>>()
        val level = root.getJSONObject("data").optString("level", "")

        for (i in 0 until limits.length()) {
            val lim = limits.getJSONObject(i)
            val reset = lim.optLong("nextResetTime", -1L).takeIf { it > 0 }
            when (lim.optString("type")) {
                "TOKENS_LIMIT" -> {
                    // (unit=3,number=5)=5小时窗口；(unit=6,number=1)=周额度
                    if (lim.optInt("unit") == 3 && lim.optInt("number") == 5 && tokens5h == null) {
                        tokens5h = Progress.of(lim.opt("percentage"), reset)
                    }
                }
                "TIME_LIMIT" -> {
                    val total = if (lim.isNull("usage")) null else lim.optInt("usage")
                    val used = if (lim.isNull("currentValue")) null else lim.optInt("currentValue")
                    val pct = if (total != null && used != null && total > 0) {
                        (used * 100.0 / total).toInt().coerceIn(0, 100)
                    } else lim.optInt("percentage", 0)
                    mcp = Progress.of(pct, reset)
                    val details = lim.optJSONArray("usageDetails")
                    if (details != null) {
                        mcpDetails = (0 until details.length()).mapNotNull { j ->
                            val d = details.getJSONObject(j)
                            val u = if (d.isNull("usage")) 0 else d.getInt("usage")
                            d.optString("modelCode").takeIf { it.isNotBlank() }?.let { it to u }
                        }
                    }
                }
            }
        }
        return ProviderSnapshot(
            providerId = "glm",
            ok = true,
            fetchedAtMillis = System.currentTimeMillis(),
            glmLevel = level.takeIf { it.isNotBlank() },
            glmTokens5h = tokens5h,
            glmMcpMonthly = mcp,
            glmMcpDetails = mcpDetails,
        )
    }
}

/** 极简 HTTP GET，Bearer 认证 + 浏览器 UA */
internal fun httpGet(url: String, bearerKey: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "Bearer $bearerKey")
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${body.take(200)}")
        }
        body
    } finally {
        conn.disconnect()
    }
}
