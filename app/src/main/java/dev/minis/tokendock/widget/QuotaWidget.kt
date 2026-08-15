package dev.minis.tokendock.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.minis.tokendock.MainActivity
import dev.minis.tokendock.data.DockState
import dev.minis.tokendock.data.Progress
import dev.minis.tokendock.data.ProviderSnapshot

class QuotaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = runCatching { dev.minis.tokendock.data.Store.read(context) }.getOrNull() ?: DockState()
        provideContent { Content(state) }
    }

    @Composable
    private fun Content(state: DockState) {
        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF17191E))
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (!state.configured) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        Title("TOKENDOCK", 13.sp)
                        Spacer(GlanceModifier.height(6.dp))
                        Body("打开 app 填入 API Key")
                    }
                } else {
                    WidgetBody(state)
                }
            }
        }
    }

    @Composable
    private fun WidgetBody(state: DockState) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Title("TOKENDOCK", 13.sp)
                Spacer(GlanceModifier.width(6.dp))
                val anyFetched = state.opencode != null || state.glm != null
                val status = when {
                    !anyFetched -> "…"
                    state.opencode?.ok == false || state.glm?.ok == false -> "异常"
                    else -> "已连接"
                }
                val statusColor = when (status) {
                    "已连接" -> Color(0xFF4ADE80)
                    "异常" -> Color(0xFFF87171)
                    else -> Color(0xFF8A8F9A)
                }
                Text(status, style = TextStyle(color = ColorProvider(statusColor), fontSize = 10.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    relativeTime(state.opencode?.fetchedAtMillis ?: state.glm?.fetchedAtMillis ?: 0L),
                    style = TextStyle(color = ColorProvider(Color(0xFF8A8F9A)), fontSize = 10.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            state.opencode?.let { ProviderRows(it) }
            state.glm?.let { ProviderRows(it) }
        }
    }

    @Composable
    private fun ProviderRows(snapshot: ProviderSnapshot) {
        val title = if (snapshot.providerId == "opencode") {
            "OpenCode Go"
        } else {
            "GLM" + (snapshot.glmLevel?.let { " ${it.uppercase()}" } ?: "")
        }
        Text(
            title,
            style = TextStyle(color = ColorProvider(Color(0xFF8A8F9A)), fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(4.dp))
        if (!snapshot.ok) {
            Text(
                "同步失败 · ${snapshot.errorMessage ?: "未知错误"}",
                style = TextStyle(color = ColorProvider(Color(0xFFF87171)), fontSize = 11.sp),
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
            return
        }
        val rows = buildList {
            snapshot.ocRolling?.let { add("5小时" to it) }
            snapshot.ocWeekly?.let { add("本周" to it) }
            snapshot.ocMonthly?.let { add("本月" to it) }
            snapshot.glmTokens5h?.let { add("5小时" to it) }
            snapshot.glmMcpMonthly?.let { add("月度MCP" to it) }
        }
        rows.forEach { (label, progress) -> ProgressRow(label, progress) }
        if (snapshot.providerId == "glm" && snapshot.glmMcpDetails.isNotEmpty()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                snapshot.glmMcpDetails.joinToString(" · ") { "${it.first} ${it.second}" },
                style = TextStyle(color = ColorProvider(Color(0xFF6E737E)), fontSize = 10.sp),
                modifier = GlanceModifier.padding(bottom = 4.dp),
            )
        }
    }

    @Composable
    private fun ProgressRow(label: String, progress: Progress) {
        Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = TextStyle(color = ColorProvider(Color(0xFFD7DAE0)), fontSize = 11.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "${progress.percent}%",
                    style = TextStyle(color = ColorProvider(Color(0xFFF5F6F8)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.height(3.dp))
            LinearProgressIndicator(
                progress = progress.percent / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = ColorProvider(barColor(progress.percent)),
                backgroundColor = ColorProvider(Color(0xFF2A2D35)),
            )
            progress.resetsAtMillis?.let {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "重置 ${countdown(it)}",
                    style = TextStyle(color = ColorProvider(Color(0xFF6E737E)), fontSize = 9.sp),
                )
            }
        }
    }

    @Composable
    private fun Title(text: String, size: androidx.compose.ui.unit.TextUnit) {
        Text(
            text,
            style = TextStyle(
                color = ColorProvider(Color(0xFFE8C36A)),
                fontSize = size,
                fontWeight = FontWeight.Bold,
            ),
        )
    }

    @Composable
    private fun Body(text: String) {
        Text(text, style = TextStyle(color = ColorProvider(Color(0xFFB8BCC6)), fontSize = 12.sp))
    }

    private fun barColor(percent: Int): Color = when {
        percent >= 90 -> Color(0xFFF87171)
        percent >= 70 -> Color(0xFFFBBF24)
        else -> Color(0xFFFACC15)
    }

    private fun relativeTime(millis: Long): String {
        if (millis <= 0) return ""
        val diffMin = (System.currentTimeMillis() - millis) / 60_000
        return when {
            diffMin < 1 -> "刚刚"
            diffMin < 60 -> "${diffMin}分钟前"
            diffMin < 1440 -> "${diffMin / 60}小时前"
            else -> "${diffMin / 1440}天前"
        }
    }

    private fun countdown(resetsAt: Long): String {
        val diff = resetsAt - System.currentTimeMillis()
        if (diff <= 0) return "即将重置"
        val hours = diff / 3_600_000
        val minutes = (diff % 3_600_000) / 60_000
        return when {
            hours >= 24 -> "约${hours / 24}天后"
            hours >= 1 -> "约${hours}h${minutes}m后"
            else -> "约${minutes}分钟后"
        }
    }
}

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuotaWidget()
}
