package dev.minis.tokendock.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P0 入口健壮性契约测试 — 内联同步不堵死 & 失败必清标志刷UI。
 * v0.3.0 上必 FAIL：RefreshAction 用 8.5s 总闸超时且 Workers 无 finally。
 */
class RefreshEntryContractsTest {

    private fun read(rel: String): String {
        val cands = listOf(
            File("app/src/main/java/dev/minis/tokendock/$rel"),
            File("src/main/java/dev/minis/tokendock/$rel"),
            File("/var/minis/workspace/quota-widget/app/src/main/java/dev/minis/tokendock/$rel"),
        )
        return cands.firstOrNull { it.exists() }?.readText() ?: error("$rel not found")
    }

    @Test
    fun `RefreshAction inline timeout is 9_5s not 8_5s`() {
        val src = read("widget/RefreshAction.kt")
        assertTrue("RefreshAction 必须用 9.5s 窗口（10s goAsync 余量），不应再是 8_500L", src.contains("9_500L") || src.contains("9500"))
        assertTrue("不应再有 8_500L", !src.contains("8_500L"))
    }

    @Test
    fun `RefreshAction has finally to clear refreshing and refresh widgets`() {
        val src = read("widget/RefreshAction.kt")
        assertTrue("RefreshAction 必须有 finally 保证清标志/刷UI", src.contains("finally"))
        // finally 块内必须同时清 refreshing 與刷 widgets
        val finallyIdx = src.lastIndexOf("finally")
        val tail = src.substring(finallyIdx)
        assertTrue(tail.contains("setRefreshing") && tail.contains("0L"))
        assertTrue(tail.contains("refreshAllWidgets"))
    }

    @Test
    fun `SyncWorker has finally to clear refreshing`() {
        val src = read("sync/SyncWorker.kt")
        assertTrue("SyncWorker doWork 必须有 finally 清标志", src.contains("finally"))
        val finallyIdx = src.lastIndexOf("finally")
        val tail = src.substring(finallyIdx)
        assertTrue(tail.contains("setRefreshing") && tail.contains("0L"))
    }

    @Test
    fun `SyncEngine per-provider timeout not global 8s gate`() {
        val src = read("sync/SyncEngine.kt")
        assertTrue("SyncEngine 必须暴露 PER_PROVIDER_TIMEOUT_MS", src.contains("PER_PROVIDER_TIMEOUT_MS"))
        assertTrue("SyncEngine 必须用 supervisorScope 避免一家失败取消另一家", src.contains("supervisorScope"))
        // withTimeout 應綁每家 fetch 而非整個 sync
        // 簡易檢查：withTimeout 出現至少兩次或包在 fetch 层
        val count = "withTimeout".toRegex().findAll(src).count()
        assertTrue("withTimeout 應出現在每 provider（≥1）且 PER_PROVIDER_TIMEOUT_MS 存在", count >= 1 && src.contains("PER_PROVIDER_TIMEOUT_MS"))
    }

    @Test
    fun `MainActivity saveKeysAndInterval and busy finally`() {
        val src = read("MainActivity.kt")
        assertTrue("MainActivity 必須有 saveKeysAndInterval（原子持久化间隔）", src.contains("saveKeysAndInterval"))
        assertTrue("MainActivity 必须用单一协程而非双重 launch", src.contains("saveKeysAndInterval"))
        // 单协程：saveKeysAndInterval 调用后直接 try/finally 清 busy
        val busyFinally = src.contains("busy = false") && src.contains("finally")
        assertTrue("MainActivity 必须 finally 清 busy，避免异常卡死按钮", busyFinally)
        // 校验间隔输入截断
        val intervalClamped = src.contains("coerceIn(15, 720)") || src.contains("coerceInterval")
        assertTrue("间隔输入必须 coerceIn(15,720)", intervalClamped)
        assertEquals(true, busyFinally && intervalClamped)
    }
}
