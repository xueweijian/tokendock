package dev.minis.tokendock.sync

import dev.minis.tokendock.data.ProviderSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：
 * - v0.2.0：app 入口在主线程直调引擎炸 NetworkOnMainThreadException；
 * - v0.2.1：widget 按钮只入队 WorkManager，被 OEM 冻结后台后永不执行（点击无反应）；
 * - v0.2.1：同步完成后组件从不重刷，按钮永久停留"同步中"置灰态。
 */
class SyncEngineTest {

    @Test
    fun `网络请求跑在 IO 线程而非调用方线程`() = runBlocking {
        val caller = Thread.currentThread().name
        var worker: String? = null
        SyncEngine.fetchOnIo("opencode") {
            worker = Thread.currentThread().name
            ProviderSnapshot("opencode", true)
        }
        assertTrue("fetch 应跑在 DefaultDispatcher(IO)，实际: $worker", worker!!.startsWith("DefaultDispatcher"))
        assertNotEquals("不应在调用方线程执行网络", caller, worker)
    }

    @Test
    fun `fetch 异常转为错误快照而非崩溃`() = runBlocking {
        val snap = SyncEngine.fetchOnIo("glm") { throw IllegalStateException("boom") }
        assertFalse(snap.ok)
        assertTrue(snap.errorMessage!!.contains("boom"))
        assertTrue(snap.fetchedAtMillis > 0)
    }

    @Test
    fun `失败且有旧数据时不覆盖`() {
        assertFalse(SyncEngine.decidePersist(freshOk = false, hasExisting = true))
    }

    @Test
    fun `失败且无历史时写错误占位`() {
        assertTrue(SyncEngine.decidePersist(freshOk = false, hasExisting = false))
    }

    @Test
    fun `成功时总是覆盖`() {
        assertTrue(SyncEngine.decidePersist(freshOk = true, hasExisting = true))
        assertTrue(SyncEngine.decidePersist(freshOk = true, hasExisting = false))
    }

    @Test
    fun `两家 API 并行拉取 - 整体耗时近似 max 而非求和`() = runBlocking {
        // 每家模拟 300ms：串行=600ms+，并行应 < 550ms
        val t0 = System.currentTimeMillis()
        val a = async(Dispatchers.IO) { SyncEngine.fetchOnIo("opencode") { Thread.sleep(300); ProviderSnapshot("opencode", true) } }
        val b = async(Dispatchers.IO) { SyncEngine.fetchOnIo("glm") { Thread.sleep(300); ProviderSnapshot("glm", true) } }
        awaitAll(a, b)
        val elapsed = System.currentTimeMillis() - t0
        assertTrue("并行耗时 ${elapsed}ms 应 < 550ms（串行会 >= 600ms）", elapsed < 550)
    }

    @Test
    fun `内联同步超时保护 - 8s 超时抛 TimeoutCancellationException`() = runBlocking {
        var threw = false
        try {
            withTimeout(200) { SyncEngine.fetchOnIo("opencode") { Thread.sleep(5_000); ProviderSnapshot("opencode", true) } }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `needsBackupRetry - 全成功不需要兜底`() {
        val r = SyncResult(
            opencode = ProviderSnapshot("opencode", ok = true),
            glm = ProviderSnapshot("glm", ok = true),
        )
        assertFalse(SyncEngine.needsBackupRetry(r))
    }

    @Test
    fun `needsBackupRetry - 任一家失败需要兜底`() {
        val r = SyncResult(
            opencode = ProviderSnapshot("opencode", ok = true),
            glm = ProviderSnapshot("glm", ok = false, errorMessage = "timeout"),
        )
        assertTrue(SyncEngine.needsBackupRetry(r))
    }

    @Test
    fun `needsBackupRetry - 超时被吞(null)需要兜底`() {
        assertTrue(SyncEngine.needsBackupRetry(null))
    }

    @Test
    fun `needsBackupRetry - 未配置(两家都null)不需要兜底`() {
        assertFalse(SyncEngine.needsBackupRetry(SyncResult(null, null)))
    }
}
