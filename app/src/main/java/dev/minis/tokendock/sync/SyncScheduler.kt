package dev.minis.tokendock.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val WORK_NAME = "tokendock_periodic_sync"

    /** 应用内每次配置变化/手动刷新后调用。间隔会收敛到 15..720 分钟（WorkManager 下限为 15 分钟）。 */
    fun schedule(context: Context, intervalMinutes: Int) {
        val minutes = intervalMinutes.coerceIn(15, 720)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** 立即跑一次一次性同步（小组件点击/下拉刷新用） */
    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build()
        )
    }
}
