package dev.minis.tokendock.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    const val WORK_NAME = "tokendock_periodic_sync"
    const val ONE_SHOT_NAME = "tokendock_manual_sync"

    /** 间隔收敛：WorkManager 下限 15 分钟 */
    fun coerceInterval(minutes: Int): Int = minutes.coerceIn(15, 720)

    /** 应用内每次配置变化后调用。 */
    fun schedule(context: Context, intervalMinutes: Int) {
        val minutes = coerceInterval(intervalMinutes).toLong()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    /**
     * 立即跑一次一次性同步（widget/应用内兜底用）。
     * 用 REPLACE + 先 cancelUniqueWork 避免旧 KEEP 堵队。
     */
    fun syncNow(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(ONE_SHOT_NAME)
        wm.enqueueUniqueWork(
            ONE_SHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }
}
