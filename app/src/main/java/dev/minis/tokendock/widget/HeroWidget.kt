package dev.minis.tokendock.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import dev.minis.tokendock.data.Store

/**
 * 方案 A「Type-first Hero」：Apple 天气/电池风。
 * 26sp 大数字（5h 窗口）+ 4dp 语义色细条 + 周月/MCP 压缩成脚注。
 */
class HeroWidget : BaseQuotaWidget() {

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
                        Header(state)
                        Spacer(GlanceModifier.height(10.dp))
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
                        Footer(state)
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(state: DockState) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Title("TokenDock", 11.sp)
            Spacer(GlanceModifier.defaultWeight())
            RefreshIconButton(refreshing = isRefreshing(state))
        }
    }

    @Composable
    private fun ProviderSection(block: ProviderBlock) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    block.title,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFD7DAE0)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(5.dp))
                StatusDot(block.status)
                Spacer(GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(4.dp))
            if (block.errorMessage != null) {
                Text(
                    "同步失败 · ${block.errorMessage}",
                    style = TextStyle(color = ColorProvider(W.red), fontSize = 10.sp),
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${block.heroPercent ?: "—"}",
                    style = TextStyle(
                        color = ColorProvider(W.ink),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    "%",
                    style = TextStyle(
                        color = ColorProvider(W.label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.padding(bottom = 3.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Body(block.heroLabel, 10.sp, W.label, GlanceModifier.padding(bottom = 4.dp))
            }
            Spacer(GlanceModifier.height(5.dp))
            LinearProgressIndicator(
                progress = (block.heroPercent ?: 0) / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = ColorProvider(barColor(block)),
                backgroundColor = ColorProvider(W.track),
            )
            Spacer(GlanceModifier.height(3.dp))
            Body(heroCaption(block.heroLabel, block.heroResetsAt), 9.sp, W.dim)
            if (block.rows.isNotEmpty()) {
                Spacer(GlanceModifier.height(5.dp))
                Body(
                    block.rows.joinToString("   ") { "${it.label} ${it.percent}%" },
                    9.sp,
                    W.label,
                )
            }
            block.footer?.let {
                Spacer(GlanceModifier.height(2.dp))
                Body(it, 9.sp, W.dim)
            }
        }
    }

    @Composable
    private fun Footer(state: DockState) {
        val ts = listOfNotNull(state.opencode?.fetchedAtMillis, state.glm?.fetchedAtMillis)
            .maxOrNull() ?: return
        Body(relativeTime(ts), 9.sp, W.dim)
    }

    private fun barColor(block: ProviderBlock): Color = when (block.status) {
        StatusLevel.OK -> W.green
        StatusLevel.WARN -> W.amber
        StatusLevel.ERROR -> if (block.errorMessage != null) W.dim else W.red
        StatusLevel.PENDING -> W.label
    }
}
