package dev.minis.tokendock.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.minis.tokendock.data.Store
import dev.minis.tokendock.sync.SyncScheduler

/**
 * 小组件"刷新"按钮回调。
 * 铁律：不碰网络。10 秒 goAsync 窗口内只做三件事：
 * ① 标记 refreshing ② 刷 UI（按钮变灰）③ 入队 KEEP 一次性同步。
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
        SyncScheduler.syncNow(context)             // 真正的同步在 WorkManager 里跑
    }
}
