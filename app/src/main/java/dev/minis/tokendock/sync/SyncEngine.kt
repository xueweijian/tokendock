package dev.minis.tokendock.sync

import android.content.Context
import dev.minis.tokendock.data.GlmApi
import dev.minis.tokendock.data.OpencodeApi
import dev.minis.tokendock.data.ProviderSnapshot
import dev.minis.tokendock.data.Store
import dev.minis.tokendock.widget.refreshAllWidgets
import kotlinx.coroutines.Dispatchers
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
 * 网络一律跑在 Dispatchers.IO —— 调用方在主线程直调也安全
 * （v0.2.0 曾因 app 入口在主线程直调炸 NetworkOnMainThreadException，此为回归修复）。
 */
object SyncEngine {

    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val state = Store.read(context)
        if (!state.configured) return@withContext SyncResult(null, null)

        val oc = state.opencodeKey.takeIf { it.isNotBlank() }
            ?.let { fetchOnIo("opencode") { OpencodeApi.fetch(it) } }
            ?.also { persist(context, it, state.opencode != null) }
        val glm = state.glmKey.takeIf { it.isNotBlank() }
            ?.let { fetchOnIo("glm") { GlmApi.fetch(it) } }
            ?.also { persist(context, it, state.glm != null) }

        runCatching { refreshAllWidgets(context) }
            .onFailure { android.util.Log.w("TokenDock", "widget refresh failed", it) }
        SyncResult(oc, glm)
    }

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
