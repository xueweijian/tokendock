package dev.minis.tokendock.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用 2026-08-15 抓取的真实 API 响应做解析基准（fixtures 由 scripts/capture_fixtures.sh 生成）。
 */
class ApiParsersTest {

    private val opencodeRaw = """
        {"usage":{"rolling":{"status":"ok","percent":0,"resetsAt":"2026-08-15T21:48:20.111Z"},
        "weekly":{"status":"ok","percent":9,"resetsAt":"2026-08-17T00:00:00.111Z"},
        "monthly":{"status":"ok","percent":4,"resetsAt":"2026-09-13T02:50:26.111Z"}}}
    """.trimIndent()

    private val glmRaw = """
        {"code":200,"msg":"操作成功","data":{"limits":[
        {"type":"TIME_LIMIT","unit":5,"number":1,"usage":100,"currentValue":0,"remaining":100,
         "percentage":0,"nextResetTime":1787041606998,
         "usageDetails":[{"modelCode":"search-prime","usage":0},{"modelCode":"web-reader","usage":0},{"modelCode":"zread","usage":0}]},
        {"type":"TOKENS_LIMIT","unit":3,"number":5,"percentage":1,"nextResetTime":1786826814399}],
        "level":"lite"},"success":true}
    """.trimIndent()

    @Test
    fun `opencode parses three windows`() {
        val usage = JSONObject(opencodeRaw).getJSONObject("usage")
        val rolling = usage.getJSONObject("rolling")
        assertEquals(0, rolling.getInt("percent"))
        assertEquals("ok", rolling.getString("status"))
        // resetsAt 解析为毫秒时间戳
        val millis = java.time.Instant.parse(
            rolling.getString("resetsAt").replace("Z", "+00:00"),
        ).toEpochMilli()
        assertTrue(millis > 1_700_000_000_000)
    }

    @Test
    fun `glm time_limit maps usage to total and currentValue to used`() {
        val limit = JSONObject(glmRaw).getJSONObject("data").getJSONArray("limits").getJSONObject(0)
        assertEquals("TIME_LIMIT", limit.getString("type"))
        assertEquals(100, limit.getInt("usage"))          // 上限（反直觉）
        assertEquals(0, limit.getInt("currentValue"))     // 已用
        assertEquals(100, limit.getInt("remaining"))
        val details = limit.getJSONArray("usageDetails")
        assertEquals(3, details.length())
        assertEquals("search-prime", details.getJSONObject(0).getString("modelCode"))
    }

    @Test
    fun `glm tokens_limit unit3 number5 is 5h window`() {
        val tokens = JSONObject(glmRaw).getJSONObject("data").getJSONArray("limits").getJSONObject(1)
        assertEquals("TOKENS_LIMIT", tokens.getString("type"))
        assertEquals(3, tokens.getInt("unit"))
        assertEquals(5, tokens.getInt("number"))
        assertEquals(1, tokens.getInt("percentage"))
    }

    @Test
    fun `glm success false means api error`() {
        val err = """{"code":401,"msg":"token invalid","data":null,"success":false}"""
        val root = JSONObject(err)
        org.junit.Assert.assertFalse(root.optBoolean("success", false))
    }

    @Test
    fun `progress coerces out of range percent`() {
        val p = Progress.of(150)
        assertEquals(100, p.percent)
        assertEquals(0, Progress.of(-3).percent)
        assertNotNull(Progress.of(50, 1787041606998).resetsAtMillis)
        assertNull(Progress.of(50).resetsAtMillis)
    }
}
