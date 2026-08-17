package dev.minis.tokendock.sync

import android.content.Context
import dev.minis.tokendock.data.GlmApi
import dev.minis.tokendock.data.OpencodeApi
import dev.minis.tokendock.data.ProviderSnapshot
import dev.minis.tokendock.data.Store
import dev.minis.tokendock.widget.refreshAllWidgets

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
 * 纯 suspend，不关心调用方是谁。调用方负责在调用前后维护 refreshing 状态。
 */
object SyncEngine {

    /** 拉取 → 存快照 → 刷新全部组件。返回结果供回显。 */
    suspend fun sync(context: Context): SyncResult {
        val state = Store.read(context)
        if (!state.configured) return SyncResult(null, null)

        val oc = state.opencodeKey.takeIf { it.isNotBlank() }
            ?.let { fetchSafely("opencode") { OpencodeApi.fetch(it) } }
            ?.also { Store.saveSnapshot(context, it) }
        val glm = state.glmKey.takeIf { it.isNotBlank() }
            ?.let { fetchSafely("glm") { GlmApi.fetch(it) } }
            ?.also { Store.saveSnapshot(context, it) }

        // 刷新失败不吞：写进错误快照可见，刷新动作本身失败记录日志
        runCatching { refreshAllWidgets(context) }
            .onFailure { android.util.Log.w("TokenDock", "widget refresh failed", it) }
        return SyncResult(oc, glm)
    }

    private inline fun fetchSafely(
        providerId: String,
        fetch: () -> ProviderSnapshot,
    ): ProviderSnapshot = runCatching { fetch() }.getOrElse { e ->
        ProviderSnapshot(
            providerId = providerId,
            ok = false,
            errorMessage = e.message?.take(120) ?: e.javaClass.simpleName,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }
}
