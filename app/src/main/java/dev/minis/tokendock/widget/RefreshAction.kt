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
 *
 * v0.3.0：点击后在 goAsync 窗口（~10s）内**内联直接同步**，不再依赖 WorkManager。
 * 原因：HyperOS/MIUI 等国产 ROM 会无限期推迟后台缓存应用的 WorkManager 任务
 * （v0.2.1 实测：点击入队后 Worker 永远不跑，按钮和数据都毫无反应）。
 * 点击本身会把进程短暂拉起，内联同步恰好在这个窗口内完成。
 *
 * 完成后**先清 refreshing 标志、再刷组件**——顺序不能反，否则按钮永久停留
 * "置灰禁用"态（v0.2.1 bug ②）。
 */
class RefreshAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // 防抖：已在同步中则忽略
        if (isRefreshing(Store.read(context))) return

        Store.setRefreshing(context, System.currentTimeMillis())
        runCatching { refreshAllWidgets(context) } // 立刻把三个组件的按钮切成"同步中"

        // 内联同步：并行拉两家 API，8.5s 截断（留余量给 10s 广播窗口）
        val result = runCatching {
            withTimeout(INLINE_SYNC_TIMEOUT_MS) { SyncEngine.sync(context) }
        }.getOrNull()

        Store.setRefreshing(context, 0L)
        runCatching { refreshAllWidgets(context) } // 无论成败：解除按钮置灰 + 展示已持久化的新数据

        // 内联失败/超时 → WorkManager 兜底再试（进程活着时通常能立即跑）
        if (SyncEngine.needsBackupRetry(result)) {
            SyncScheduler.syncNow(context)
        }
    }

    companion object {
        internal val INLINE_SYNC_TIMEOUT_MS: Long = 8_500L
    }
}
