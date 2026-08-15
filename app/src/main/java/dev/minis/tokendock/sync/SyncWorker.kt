package dev.minis.tokendock.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.minis.tokendock.data.GlmApi
import dev.minis.tokendock.data.OpencodeApi
import dev.minis.tokendock.data.ProviderSnapshot
import dev.minis.tokendock.data.Store
import dev.minis.tokendock.widget.QuotaWidget

/** 后台同步 Worker：拉两个 API → 存快照 → 刷新所有小组件 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val state = Store.read(context)
        if (!state.configured) return Result.success()

        state.opencodeKey.takeIf { it.isNotBlank() }?.let { key ->
            Store.saveSnapshot(context, fetchSafely("opencode") { OpencodeApi.fetch(key) })
        }
        state.glmKey.takeIf { it.isNotBlank() }?.let { key ->
            Store.saveSnapshot(context, fetchSafely("glm") { GlmApi.fetch(key) })
        }
        runCatching { QuotaWidget().updateAll(context) }
        return Result.success()
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
