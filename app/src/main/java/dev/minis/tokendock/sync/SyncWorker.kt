package dev.minis.tokendock.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.minis.tokendock.data.Store

/** 后台同步 Worker：只做状态维护 + 调引擎 + finally 清标志刷UI */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Store.setRefreshing(context, System.currentTimeMillis())
        return try {
            SyncEngine.sync(context)
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.retry()
        } finally {
            Store.setRefreshing(context, 0L)
            runCatching { dev.minis.tokendock.widget.refreshAllWidgets(applicationContext) }
        }
    }
}
