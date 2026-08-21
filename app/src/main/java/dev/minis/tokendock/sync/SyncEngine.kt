package dev.minis.tokendock.sync

import android.content.Context
import dev.minis.tokendock.data.GlmApi
import dev.minis.tokendock.data.OpencodeApi
import dev.minis.tokendock.data.ProviderSnapshot
import dev.minis.tokendock.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** 一次同步的结果（app 内回显用） */
data class SyncResult(
    val opencode: ProviderSnapshot?,
    val glm: ProviderSnapshot?,
) {
    val allOk: Boolean get() = (opencode?.ok ?: true) && (glm?.ok ?: true)
    val anyAttempted: Boolean get() = opencode != null || glm != null
}

/**
 * 同步引擎：三个入口（widget 刷新按钮 / app 手动同步 / 周期任务）共用。
 *
 * v0.3.0 变更：
 * - 两家 API 并行拉取（整体耗时 = max(OC, GLM) 而非求和），单家 8s 超时，
 *   保证 widget 点击的内联同步（10s goAsync 窗口）能跑完；
 * - 引擎不再负责刷组件 UI —— refreshing 标志清除后必须重刷一次组件
 *   （否则按钮永久停留"置灰禁用"态，v0.2.1 实测 bug），刷新时机交由各入口
 *   在「清标志之后」统一执行。
 *
 * 网络一律跑在 Dispatchers.IO —— 调用方在主线程直调也安全
 * （v0.2.0 曾因 app 入口在主线程直调炸 NetworkOnMainThreadException）。
 */
object SyncEngine {

    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val state = Store.read(context)
        if (!state.configured) return@withContext SyncResult(null, null)

        coroutineScope {
            val ocDeferred = state.opencodeKey.takeIf { it.isNotBlank() }
                ?.let { key -> async { fetchOnIo("opencode") { OpencodeApi.fetch(key) } } }
            val glmDeferred = state.glmKey.takeIf { it.isNotBlank() }
                ?.let { key -> async { fetchOnIo("glm") { GlmApi.fetch(key) } } }
            // 各家拉完立即持久化：即使另一家超时/失败，已完成的新数据不丢
            val oc = ocDeferred?.await()?.also { persist(context, it, state.opencode != null) }
            val glm = glmDeferred?.await()?.also { persist(context, it, state.glm != null) }
            SyncResult(oc, glm)
        }
    }

    /** 内联同步失败/超时后是否需要 WorkManager 兜底重试 */
    internal fun needsBackupRetry(r: SyncResult?): Boolean =
        r == null || (r.anyAttempted && !r.allOk)

    /**
     * 网络请求永远在 IO 线程执行（fetch 是阻塞式 HttpURLConnection）。
     * 单独暴露供单测断言线程。
     */
    internal suspend fun fetchOnIo(
        providerId: String,
        fetch: () -> ProviderSnapshot,
    ): ProviderSnapshot = withContext(Dispatchers.IO) {
        runCatching { fetch() }.getOrElse { e ->
            ProviderSnapshot(
                providerId = providerId,
                ok = false,
                errorMessage = e.message?.take(120) ?: e.javaClass.simpleName,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    /**
     * 持久化决策：失败不覆盖旧快照（保护上次成功数据，组件继续显示旧额度+旧时间戳）；
     * 只有从未成功过时才写错误占位（让组件有东西可显示）。
     */
    internal fun decidePersist(freshOk: Boolean, hasExisting: Boolean): Boolean =
        freshOk || !hasExisting

    private suspend fun persist(context: Context, fresh: ProviderSnapshot, hasExisting: Boolean) {
        if (decidePersist(fresh.ok, hasExisting)) Store.saveSnapshot(context, fresh)
    }
}
