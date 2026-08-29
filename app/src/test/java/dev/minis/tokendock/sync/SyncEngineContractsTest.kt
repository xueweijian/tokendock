package dev.minis.tokendock.sync

import dev.minis.tokendock.data.Progress
import dev.minis.tokendock.data.ProviderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 持久化/引擎契约测试 — TDD 锁死"失败也刷新时间戳/错误可见"与并行/超时语义。
 * v0.3.0 上部分用例必须 FAIL。
 */
class SyncEngineContractsTest {

    // ---- decidePersist / merge —— 失败也必须让时间戳动起来 ----

    @Test
    fun `失败有旧数据时仍需写错误分支用于刷新时间戳`() {
        // decidePersist 语义在 v0.3.1 改为：由 mergeErrorPreservingData 负责
        // 本用例锁死 mergedError 的可见性
        val existing = ProviderSnapshot("opencode", ok = true, fetchedAtMillis = 1000L, ocRolling = Progress(12, null))
        val freshErr = ProviderSnapshot("opencode", ok = false, errorMessage = "HTTP 500", fetchedAtMillis = 9999L)
        val merged = SyncEngine.mergePreservingData(existing, freshErr)
        assertFalse(merged.ok)
        assertEquals("HTTP 500", merged.errorMessage)
        assertEquals(9999L, merged.fetchedAtMillis)
        assertEquals(12, merged.ocRolling!!.percent) // 旧数据保留
    }

    @Test
    fun `成功直接覆盖`() {
        val fresh = ProviderSnapshot("glm", ok = true, fetchedAtMillis = 555L, glmTokens5h = Progress(33, null))
        val merged = SyncEngine.mergePreservingData(null, fresh)
        assertTrue(merged.ok)
        assertEquals(33, merged.glmTokens5h!!.percent)
        assertEquals(555L, merged.fetchedAtMillis)
    }

    @Test
    fun `失败无历史时错误占位`() {
        val freshErr = ProviderSnapshot("glm", ok = false, errorMessage = "timeout", fetchedAtMillis = 777L)
        val merged = SyncEngine.mergePreservingData(null, freshErr)
        assertFalse(merged.ok)
        assertEquals("timeout", merged.errorMessage)
        assertEquals(777L, merged.fetchedAtMillis)
    }

    // ---- per-provider 超时：超时异常被转为错误快照，不抛 ----

    @Test
    fun `perProviderTimeoutMs constant is 12s`() {
        assertEquals(12_000L, SyncEngine.PER_PROVIDER_TIMEOUT_MS)
    }

    // ---- supervisorScope：接口可测性契约 ----

    @Test
    fun `needsBackupRetry semantics`() {
        assertFalse(SyncEngine.needsBackupRetry(SyncResult(ProviderSnapshot("opencode", true), ProviderSnapshot("glm", true))))
        assertTrue(SyncEngine.needsBackupRetry(SyncResult(ProviderSnapshot("opencode", true), ProviderSnapshot("glm", false, errorMessage = "x"))))
        assertTrue(SyncEngine.needsBackupRetry(null))
        assertFalse(SyncEngine.needsBackupRetry(SyncResult(null, null)))
    }
}
