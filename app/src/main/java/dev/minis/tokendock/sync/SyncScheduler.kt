package dev.minis.tokendock.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val WORK_NAME = "tokendock_periodic_sync"
    private const val ONE_SHOT_NAME = "tokendock_manual_sync"

    /** 应用内每次配置变化后调用。间隔收敛到 15..720 分钟（WorkManager 下限 15 分钟）。 */
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

    /**
     * 立即跑一次一次性同步（widget 刷新按钮用）。
     * KEEP 策略防连点堆请求；widget 点击把进程拉到前台优先级，
     * GreedyScheduler 即刻执行，无需 expedited。
     */
    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }
}
