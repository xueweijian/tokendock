package dev.minis.tokendock.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.minis.tokendock.data.Store
import dev.minis.tokendock.sync.SyncEngine
import dev.minis.tokendock.sync.SyncScheduler
import kotlinx.coroutines.withTimeout

/**
 * 小组件"刷新"按钮回调。
 * v0.3.1：10s goAsync 窗口内 9.5s 内联，finally 必清标志刷UI。
 */
class RefreshAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        if (isRefreshing(Store.read(context))) return

        Store.setRefreshing(context, System.currentTimeMillis())
        runCatching { refreshAllWidgets(context) }

        var result: dev.minis.tokendock.sync.SyncResult? = null
        try {
            result = withTimeout(INLINE_SYNC_TIMEOUT_MS) { SyncEngine.sync(context) }
        } catch (_: Exception) {
            // TimeoutCancellationException -> result stays null, will fallback to WM
        } finally {
            Store.setRefreshing(context, 0L)
            runCatching { refreshAllWidgets(context) }
        }

        if (SyncEngine.needsBackupRetry(result)) {
            SyncScheduler.syncNow(context)
        }
    }

    companion object {
        internal const val INLINE_SYNC_TIMEOUT_MS: Long = 9_500L
    }
}
