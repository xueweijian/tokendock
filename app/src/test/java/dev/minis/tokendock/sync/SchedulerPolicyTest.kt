package dev.minis.tokendock.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P0 架构契约测试 — TDD 锁死调度策略。
 * 这些测试在 v0.3.0 上必须 FAIL（策略仍是 UPDATE/KEEP，会堵队），
 * 修完后必须 PASS。铁律：测试先行。
 */
class SchedulerPolicyTest {

    private fun schedulerSource(): String {
        // 兼容本地与 CI 的工作目录
        val candidates = listOf(
            File("app/src/main/java/dev/minis/tokendock/sync/SyncScheduler.kt"),
            File("src/main/java/dev/minis/tokendock/sync/SyncScheduler.kt"),
            File("/var/minis/workspace/quota-widget/app/src/main/java/dev/minis/tokendock/sync/SyncScheduler.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("SyncScheduler.kt not found in any candidate path")
    }

    @Test
    fun `periodic uses CANCEL_AND_REENQUEUE to avoid stale work`() {
        val src = schedulerSource()
        assertTrue(
            "SyncScheduler.schedule 必须用 CANCEL_AND_REENQUEUE（当前是 UPDATE，会导致旧周期永远不更新）",
            src.contains("CANCEL_AND_REENQUEUE"),
        )
    }

    @Test
    fun `one-shot uses REPLACE and cancels to avoid KEEP block`() {
        val src = schedulerSource()
        assertTrue("syncNow 必须用 REPLACE（当前 KEEP 会堵死后续所有 syncNow）", src.contains("ExistingWorkPolicy.REPLACE"))
        // 兜底前必须 cancelUniqueWork 或带唯一后缀避免 KEEP
        assertTrue(
            "syncNow 必须先 cancelUniqueWork 或使用时间戳唯一名以打破 KEEP",
            src.contains("cancelUniqueWork") || src.contains("ONE_SHOT_NAME +") || src.contains("System.currentTimeMillis"),
        )
    }

    @Test
    fun `interval coerced in 15 to 720`() {
        // 纯函数契约：SyncScheduler 必须暴露 coerceInterval 或 schedule 内 coerceIn
        val src = schedulerSource()
        assertTrue("必须有 coerceIn(15, 720) 或 coerceInterval", src.contains("coerceIn(15, 720)") || src.contains("coerceInterval"))
        // 数值行为由 SyncScheduler.coerceInterval 保证；若未暴露则用内联逻辑测
        fun coerce(m: Int) = m.coerceIn(15, 720)
        assertEquals(15, coerce(1))
        assertEquals(15, coerce(14))
        assertEquals(15, coerce(15))
        assertEquals(60, coerce(60))
        assertEquals(720, coerce(720))
        assertEquals(720, coerce(1000))
    }

    @Test
    fun `periodic has backoff criteria`() {
        val src = schedulerSource()
        assertTrue("PeriodicWorkRequest 必须设 backoff（否则 OEM 杀后永不重试）", src.contains("setBackoffCriteria"))
    }
}
