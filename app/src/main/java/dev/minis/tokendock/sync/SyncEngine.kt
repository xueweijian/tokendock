package dev.minis.tokendock.sync

import android.content.Context
import dev.minis.tokendock.data.GlmApi
import dev.minis.tokendock.data.OpencodeApi
import dev.minis.tokendock.data.ProviderSnapshot
import dev.minis.tokendock.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
 * v0.3.1：
 * - supervisorScope + per-provider withTimeout(12s)，一家失败/超时不牵连另一家；
 * - 失败也merge落盘（保留旧 percent，更新 errorMessage+fetchedAt），保证时间戳每次都动；
 * - 引擎不再刷组件 UI，由各入口在 finally 清标志后统一刷。
 */
object SyncEngine {

    const val PER_PROVIDER_TIMEOUT_MS: Long = 12_000L

    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val state = Store.read(context)
        if (!state.configured) return@withContext SyncResult(null, null)
        supervisorScope {
            val ocDeferred = state.opencodeKey.takeIf { it.isNotBlank() }
                ?.let { key -> async { fetchWithTimeout("opencode") { OpencodeApi.fetch(key) } } }
            val glmDeferred = state.glmKey.takeIf { it.isNotBlank() }
                ?.let { key -> async { fetchWithTimeout("glm") { GlmApi.fetch(key) } } }
            // 各家拉完立即持久化：一家失败不丢另一家；失败也落盘以刷新时间戳
            val oc = ocDeferred?.await()?.let { fresh ->
                val merged = mergePreservingData(state.opencode, fresh)
                persistMerged(context, merged)
                merged
            }
            val glm = glmDeferred?.await()?.let { fresh ->
                val merged = mergePreservingData(state.glm, fresh)
                persistMerged(context, merged)
                merged
            }
            SyncResult(oc, glm)
        }
    }

    internal suspend fun fetchWithTimeout(
        providerId: String,
        fetch: () -> ProviderSnapshot,
    ): ProviderSnapshot = runCatching {
        withTimeout(PER_PROVIDER_TIMEOUT_MS) { fetchOnIo(providerId, fetch) }
    }.getOrElse { e ->
        // TimeoutCancellationException 也转为错误快照
        ProviderSnapshot(
            providerId = providerId,
            ok = false,
            errorMessage = when (e) {
                is kotlinx.coroutines.TimeoutCancellationException -> "请求超时(${PER_PROVIDER_TIMEOUT_MS / 1000}s)"
                else -> e.message?.take(120) ?: e.javaClass.simpleName
            },
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    /** 内联同步失败/超时后是否需要 WorkManager 兜底重试 */
    internal fun needsBackupRetry(r: SyncResult?): Boolean =
        r == null || (r.anyAttempted && !r.allOk)

    /**
     * 网络请求永远在 IO 线程执行（fetch 是阻塞式 HttpURLConnection）。
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
     * 失败时保留历史 percent，只覆盖 ok/errorMessage/fetchedAt。
     * 成功或无历史时直接用新快照。
     */
    fun mergePreservingData(existing: ProviderSnapshot?, fresh: ProviderSnapshot): ProviderSnapshot {
        if (fresh.ok || existing == null) return fresh
        // 保留旧的进度数据，仅刷新错误与时间戳，确保用户能看到"刚刚尝试过但失败了"
        return existing.copy(
            ok = false,
            errorMessage = fresh.errorMessage,
            fetchedAtMillis = fresh.fetchedAtMillis,
        )
    }

    internal fun decidePersist(freshOk: Boolean, hasExisting: Boolean): Boolean =
        freshOk || !hasExisting

    private suspend fun persistMerged(context: Context, merged: ProviderSnapshot) {
        Store.saveSnapshot(context, merged)
    }
}
