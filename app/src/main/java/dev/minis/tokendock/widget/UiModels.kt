package dev.minis.tokendock.widget

import dev.minis.tokendock.data.DockState
import dev.minis.tokendock.data.ProviderSnapshot

/**
 * UI 模型层：DockState → 各组件视图模型的纯函数映射。
 * 全部无副作用、无 Android 依赖，单测覆盖。
 */

/** 状态点语义色档位 */
enum class StatusLevel { OK, WARN, ERROR, PENDING }

/** 一个厂商区块的视图模型 */
data class ProviderBlock(
    val providerId: String,
    val title: String,               // "OpenCode Go" / "GLM LITE"
    val status: StatusLevel,
    val heroPercent: Int?,           // 5h 窗口（主数字）；失败时 null → UI 显示 "—"
    val heroLabel: String,           // "5h"
    val heroResetsAt: Long?,         // hero 重置时间
    val rows: List<RowSpec>,         // 次要行（周/月/MCP）
    val footer: String?,             // 脚注（MCP 明细）
    val errorMessage: String?,
)

data class RowSpec(
    val label: String,               // "本周"
    val percent: Int,
    val resetsAtMillis: Long?,
)

/** 脏状态兜底窗口 */
const val STALE_REFRESHING_MS = 2 * 60 * 1000L

/** 从状态推导当前是否处于"同步中"（含 2 分钟兜底） */
fun isRefreshing(state: DockState, nowMillis: Long = System.currentTimeMillis()): Boolean =
    state.refreshingSinceMillis > 0 &&
        nowMillis - state.refreshingSinceMillis < STALE_REFRESHING_MS

/** 语义色阈值：<70 绿，70-89 琥珀，>=90 红 */
fun statusLevelOf(percent: Int): StatusLevel = when {
    percent >= 90 -> StatusLevel.ERROR
    percent >= 70 -> StatusLevel.WARN
    else -> StatusLevel.OK
}

/** 两个厂商区块（顺序固定 opencode → glm，未配置的跳过） */
fun buildBlocks(state: DockState): List<ProviderBlock> = buildList {
    state.opencode?.let { add(toBlock(it)) }
    state.glm?.let { add(toBlock(it)) }
}

private fun toBlock(s: ProviderSnapshot): ProviderBlock {
    val isGlm = s.providerId == "glm"
    val title = if (isGlm) {
        "GLM" + (s.glmLevel?.let { " ${it.uppercase()}" } ?: "")
    } else {
        "OpenCode Go"
    }
    val hero: dev.minis.tokendock.data.Progress? =
        if (s.ok) (if (isGlm) s.glmTokens5h else s.ocRolling) else null
    val rows = if (!s.ok) {
        emptyList()
    } else {
        buildList {
            if (!isGlm) {
                s.ocWeekly?.let { add(RowSpec("本周", it.percent, it.resetsAtMillis)) }
                s.ocMonthly?.let { add(RowSpec("本月", it.percent, it.resetsAtMillis)) }
            } else {
                s.glmMcpMonthly?.let { add(RowSpec("月度MCP", it.percent, it.resetsAtMillis)) }
            }
        }
    }
    val footer = if (isGlm && s.ok && s.glmMcpDetails.isNotEmpty()) {
        s.glmMcpDetails.joinToString(" · ") { "${labelOf(it.first)} ${it.second}" }
    } else {
        null
    }
    return ProviderBlock(
        providerId = s.providerId,
        title = title,
        status = if (!s.ok) StatusLevel.ERROR else statusLevelOf(hero?.percent ?: 0),
        heroPercent = hero?.percent,
        heroLabel = "5h",
        heroResetsAt = hero?.resetsAtMillis,
        rows = rows,
        footer = footer,
        errorMessage = if (!s.ok) (s.errorMessage ?: "未知错误") else null,
    )
}

/** MCP 工具 code → 中文名 */
fun labelOf(code: String): String = when (code) {
    "search-prime" -> "搜索"
    "web-reader" -> "读取"
    "zread" -> "Zread"
    else -> code
}

/** Hero 行文案："5h · 重置约2h5m后" */
fun heroCaption(heroLabel: String, resetsAt: Long?, nowMillis: Long = System.currentTimeMillis()): String {
    val reset = resetsAt?.let { countdown(it, nowMillis) } ?: "—"
    return "$heroLabel · 重置$reset"
}

/** 倒计时文案 */
fun countdown(resetsAt: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = resetsAt - nowMillis
    if (diff <= 0) return "即将重置"
    val hours = diff / 3_600_000
    val minutes = (diff % 3_600_000) / 60_000
    return when {
        hours >= 24 -> "约${hours / 24}天后"
        hours >= 1 -> "约${hours}h${minutes}m后"
        else -> "约${minutes}分钟后"
    }
}

/** 相对时间文案（组件底/右上角时间戳） */
fun relativeTime(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (millis <= 0) return ""
    val diffMin = (nowMillis - millis) / 60_000
    return when {
        diffMin < 1 -> "刚刚"
        diffMin < 60 -> "${diffMin}分钟前"
        diffMin < 1440 -> "${diffMin / 60}小时前"
        else -> "${diffMin / 1440}天前"
    }
}
