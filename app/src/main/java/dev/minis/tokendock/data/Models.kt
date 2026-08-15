package dev.minis.tokendock.data

/** 单条进度（百分比 0..100 + 重置时间戳毫秒） */
data class Progress(
    val percent: Int,
    val resetsAtMillis: Long?,
) {
    companion object {
        fun of(percent: Number?, resetsAt: Long? = null): Progress =
            Progress(percent?.toInt()?.coerceIn(0, 100) ?: 0, resetsAt)
    }
}

/** 一个 provider 的完整快照 */
data class ProviderSnapshot(
    val providerId: String,          // "opencode" | "glm"
    val ok: Boolean,                 // API 调用是否成功
    val errorMessage: String? = null,
    val fetchedAtMillis: Long = 0L,
    // OpenCode Go
    val ocRolling: Progress? = null, // 5h 滚动窗口
    val ocWeekly: Progress? = null,
    val ocMonthly: Progress? = null,
    // GLM
    val glmLevel: String? = null,
    val glmTokens5h: Progress? = null,           // 5h Token 窗口（只有百分比）
    val glmMcpMonthly: Progress? = null,         // MCP 月配额
    val glmMcpDetails: List<Pair<String, Int>> = emptyList(), // toolCode -> 已用
)

/** DataStore 持久化的整体状态 */
data class DockState(
    val opencodeKey: String = "",
    val glmKey: String = "",
    val opencode: ProviderSnapshot? = null,
    val glm: ProviderSnapshot? = null,
    val intervalMinutes: Int = 60,
) {
    val configured: Boolean get() = opencodeKey.isNotBlank() || glmKey.isNotBlank()
}
