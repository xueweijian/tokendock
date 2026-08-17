package dev.minis.tokendock.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.minis.tokendock.data.Store

/** 后台同步 Worker：瘦身，只做状态维护 + 调引擎 */
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
        } finally {
            Store.setRefreshing(context, 0L)
        }
    }
}
