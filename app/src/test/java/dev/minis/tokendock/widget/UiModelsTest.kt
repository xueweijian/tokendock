package dev.minis.tokendock.widget

import dev.minis.tokendock.data.DockState
import dev.minis.tokendock.data.Progress
import dev.minis.tokendock.data.ProviderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiModelsTest {

    private val now = 1_800_000_000_000L

    private fun ocSnap(
        ok: Boolean = true,
        rolling: Int? = 12,
        weekly: Int? = 9,
        monthly: Int? = 4,
        err: String? = null,
    ) = ProviderSnapshot(
        providerId = "opencode",
        ok = ok,
        errorMessage = err,
        fetchedAtMillis = now,
        ocRolling = rolling?.let { Progress(it, now + 3_600_000) },
        ocWeekly = weekly?.let { Progress(it, now + 86_400_000) },
        ocMonthly = monthly?.let { Progress(it, now + 30 * 86_400_000) },
    )

    private fun glmSnap(
        ok: Boolean = true,
        tokens5h: Int? = 7,
        mcp: Int? = 0,
        level: String? = "lite",
        err: String? = null,
    ) = ProviderSnapshot(
        providerId = "glm",
        ok = ok,
        errorMessage = err,
        fetchedAtMillis = now,
        glmLevel = level,
        glmTokens5h = tokens5h?.let { Progress(it, now + 7_200_000) },
        glmMcpMonthly = mcp?.let { Progress(it, null) },
        glmMcpDetails = listOf("search-prime" to 42, "web-reader" to 8),
    )

    // ---- statusLevelOf ----

    @Test
    fun `status thresholds`() {
        assertEquals(StatusLevel.OK, statusLevelOf(0))
        assertEquals(StatusLevel.OK, statusLevelOf(69))
        assertEquals(StatusLevel.WARN, statusLevelOf(70))
        assertEquals(StatusLevel.WARN, statusLevelOf(89))
        assertEquals(StatusLevel.ERROR, statusLevelOf(90))
        assertEquals(StatusLevel.ERROR, statusLevelOf(100))
    }

    // ---- isRefreshing 脏状态兜底 ----

    @Test
    fun `refreshing true within window`() {
        val s = DockState(refreshingSinceMillis = now)
        assertTrue(isRefreshing(s, now + 30_000))
    }

    @Test
    fun `refreshing stale after 2 minutes`() {
        val s = DockState(refreshingSinceMillis = now)
        assertFalse(isRefreshing(s, now + 2 * 60_000 + 1))
    }

    @Test
    fun `refreshing false when zero`() {
        assertFalse(isRefreshing(DockState(refreshingSinceMillis = 0L), now))
    }

    // ---- buildBlocks ----

    @Test
    fun `blocks order opencode then glm`() {
        val state = DockState(opencode = ocSnap(), glm = glmSnap())
        val blocks = buildBlocks(state)
        assertEquals(2, blocks.size)
        assertEquals("opencode", blocks[0].providerId)
        assertEquals("glm", blocks[1].providerId)
    }

    @Test
    fun `unconfigured provider omitted`() {
        val state = DockState(glm = glmSnap())
        assertEquals(1, buildBlocks(state).size)
        assertEquals("glm", buildBlocks(state)[0].providerId)
    }

    @Test
    fun `hero is 5h window for both providers`() {
        val blocks = buildBlocks(DockState(opencode = ocSnap(rolling = 33), glm = glmSnap(tokens5h = 66)))
        assertEquals(33, blocks[0].heroPercent)
        assertEquals(66, blocks[1].heroPercent)
    }

    @Test
    fun `glm title includes level uppercased`() {
        val blocks = buildBlocks(DockState(glm = glmSnap(level = "lite")))
        assertEquals("GLM LITE", blocks[0].title)
    }

    @Test
    fun `opencode rows are weekly and monthly`() {
        val rows = buildBlocks(DockState(opencode = ocSnap()))[0].rows
        assertEquals(listOf("本周", "本月"), rows.map { it.label })
    }

    @Test
    fun `glm rows are mcp only`() {
        val rows = buildBlocks(DockState(glm = glmSnap()))[0].rows
        assertEquals(listOf("月度MCP"), rows.map { it.label })
    }

    @Test
    fun `glm footer joins mcp details with chinese labels`() {
        val footer = buildBlocks(DockState(glm = glmSnap()))[0].footer
        assertEquals("搜索 42 · 读取 8", footer)
    }

    @Test
    fun `failed snapshot yields error status and message`() {
        val blocks = buildBlocks(DockState(opencode = ocSnap(ok = false, err = "HTTP 403")))
        assertEquals(StatusLevel.ERROR, blocks[0].status)
        assertEquals("HTTP 403", blocks[0].errorMessage)
        assertNull(blocks[0].heroPercent) // 快照失败时不给 hero
    }

    @Test
    fun `ok snapshot has no error message`() {
        val b = buildBlocks(DockState(opencode = ocSnap()))[0]
        assertNull(b.errorMessage)
    }

    // ---- labelOf ----

    @Test
    fun `mcp label mapping`() {
        assertEquals("搜索", labelOf("search-prime"))
        assertEquals("读取", labelOf("web-reader"))
        assertEquals("Zread", labelOf("zread"))
        assertEquals("custom-tool", labelOf("custom-tool"))
    }

    // ---- countdown ----

    @Test
    fun `countdown formats`() {
        assertEquals("即将重置", countdown(now, now))
        assertEquals("约4分钟后", countdown(now + 4 * 60_000, now))
        assertEquals("约2h5m后", countdown(now + (2 * 60 + 5) * 60_000, now))
        assertEquals("约2天后", countdown(now + 50 * 3_600_000, now))
    }

    // ---- relativeTime ----

    @Test
    fun `relative time formats`() {
        assertEquals("刚刚", relativeTime(now, now))
        assertEquals("刚刚", relativeTime(now, now + 59_000))
        assertEquals("5分钟前", relativeTime(now, now + 5 * 60_000))
        assertEquals("3小时前", relativeTime(now, now + 3 * 3_600_000))
        assertEquals("2天前", relativeTime(now, now + 48 * 3_600_000))
        assertEquals("", relativeTime(0L, now))
    }

    // ---- heroCaption ----

    @Test
    fun `hero caption combines label and reset`() {
        assertEquals("5h · 重置约2h5m后", heroCaption("5h", now + (2 * 60 + 5) * 60_000, now))
        assertEquals("5h · 重置—", heroCaption("5h", null, now))
    }

    // ---- 失败快照 hero 降级（heroPercent=null → UI 显示 "—"） ----

    @Test
    fun `null rolling yields null hero`() {
        val b = buildBlocks(DockState(opencode = ocSnap(rolling = null)))[0]
        assertNull(b.heroPercent)
    }
}
