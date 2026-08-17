package dev.minis.tokendock.sync

import dev.minis.tokendock.data.ProviderSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：v0.2.0 曾因 app 入口在主线程直调引擎炸 NetworkOnMainThreadException。
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
}
