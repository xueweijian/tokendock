package dev.minis.tokendock.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.minis.tokendock.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机/升级后重挂周期任务。
 * OEM 升级、清理后台、Force Stop 后 WorkManager 队列会被清空，此接收器保证自愈。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = Store.read(context)
                if (state.configured) {
                    SyncScheduler.schedule(context, state.intervalMinutes)
                }
            } catch (_: Exception) {
                // 忽略：下一次 App 启动仍会重挂
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
