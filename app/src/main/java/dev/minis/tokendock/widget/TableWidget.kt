package dev.minis.tokendock.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
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
import dev.minis.tokendock.data.Store

/**
 * 方案 C「数据表」：Linear/Vercel dashboard 风。
 * 每厂商完整列出所有窗口行，信息密度最高。
 */
class TableWidget : BaseQuotaWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = runCatching { Store.read(context) }.getOrNull() ?: DockState()
        provideContent { Content(state) }
    }

    @Composable
    private fun Content(state: DockState) {
        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(W.card)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (!state.configured) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        Title("TokenDock", 12.sp)
                        Spacer(GlanceModifier.height(6.dp))
                        Body("打开 app 填入 API Key", color = W.dim)
                    }
                } else {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Title("TokenDock", 11.sp)
                            Spacer(GlanceModifier.defaultWeight())
                            RefreshIconButton(refreshing = isRefreshing(state))
                        }
                        Spacer(GlanceModifier.height(8.dp))
                        val blocks = buildBlocks(state)
                        blocks.forEachIndexed { i, block ->
                            if (i > 0) {
                                Spacer(GlanceModifier.height(8.dp))
                                HairLine()
                                Spacer(GlanceModifier.height(8.dp))
                            }
                            ProviderSection(block)
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        val ts = listOfNotNull(
                            state.opencode?.fetchedAtMillis,
                            state.glm?.fetchedAtMillis,
                        ).maxOrNull()
                        if (ts != null) Body(relativeTime(ts), 9.sp, W.dim)
                    }
                }
            }
        }
    }

    @Composable
    private fun ProviderSection(block: ProviderBlock) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(block.status)
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    block.title,
                    style = TextStyle(
                        color = ColorProvider(W.ink),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                if (block.errorMessage == null) {
                    Text(
                        heroCaption(block.heroLabel, block.heroResetsAt),
                        style = TextStyle(color = ColorProvider(W.dim), fontSize = 8.sp),
                    )
                }
            }
            Spacer(GlanceModifier.height(5.dp))
            if (block.errorMessage != null) {
                Text(
                    "同步失败 · ${block.errorMessage}",
                    style = TextStyle(color = ColorProvider(W.red), fontSize = 10.sp),
                )
                return@Column
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    block.heroLabel + "窗口",
                    style = TextStyle(color = ColorProvider(W.label), fontSize = 10.sp),
                    modifier = GlanceModifier.width(52.dp),
                )
                Text(
                    "${block.heroPercent ?: "—"}%",
                    style = TextStyle(
                        color = ColorProvider(W.ink),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.width(34.dp),
                )
                LinearProgressIndicator(
                    progress = (block.heroPercent ?: 0) / 100f,
                    modifier = GlanceModifier.defaultWeight().height(4.dp),
                    color = ColorProvider(W.statusColor(block.status)),
                    backgroundColor = ColorProvider(W.track),
                )
            }
            block.rows.forEach { row ->
                Spacer(GlanceModifier.height(4.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.label,
                        style = TextStyle(color = ColorProvider(W.label), fontSize = 10.sp),
                        modifier = GlanceModifier.width(52.dp),
                    )
                    Text(
                        "${row.percent}%",
                        style = TextStyle(color = ColorProvider(W.label), fontSize = 10.sp),
                        modifier = GlanceModifier.width(34.dp),
                    )
                    LinearProgressIndicator(
                        progress = row.percent / 100f,
                        modifier = GlanceModifier.defaultWeight().height(3.dp),
                        color = ColorProvider(W.statusColor(statusLevelOf(row.percent))),
                        backgroundColor = ColorProvider(W.track),
                    )
                }
            }
            block.footer?.let {
                Spacer(GlanceModifier.height(4.dp))
                Body(it, 8.sp, W.dim)
            }
        }
    }
}
