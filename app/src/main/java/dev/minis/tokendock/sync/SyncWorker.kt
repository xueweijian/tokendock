package dev.minis.tokendock.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.minis.tokendock.data.Store

/** 后台同步 Worker：瘦身，只做状态维护 + 调引擎 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker() {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Store.setRefreshing(context, System.currentTimeMillis())
        return try {
            SyncEngine.sync(context)
            // 清标志后再刷新组件：顺序不能反（否则按钮永久置灰，v0.2.1 bug ②）
            Store.setRefreshing(context, 0L)
            runCatching { refreshAllWidgetsSafe() }
            Result.success()
        } catch (e: Exception) {
            Store.setRefreshing(context, 0L)
            runCatching { refreshAllWidgetsSafe() }
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.retry()
        }
    }

    private suspend fun refreshAllWidgetsSafe() {
        dev.minis.tokendock.widget.refreshAllWidgets(applicationContext)
    }
}
