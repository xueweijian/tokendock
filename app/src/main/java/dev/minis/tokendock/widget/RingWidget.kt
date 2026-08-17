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
import androidx.glance.appwidget.Image
import androidx.glance.appwidget.ImageProvider
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
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.minis.tokendock.MainActivity
import dev.minis.tokendock.data.DockState
import dev.minis.tokendock.data.Store

/**
 * 方案 B「双环」：Fitness rings 风。
 * OC：外=5h 中=周 内=月；GLM：外=5h 内=MCP。
 * 环体 Canvas 位图渲染，中心叠 5h 大数字。
 */
class RingWidget : BaseQuotaWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = runCatching { Store.read(context) }.getOrNull() ?: DockState()
        val density = context.resources.displayMetrics.density
        provideContent { Content(state, density) }
    }

    @Composable
    private fun Content(state: DockState, density: Float) {
        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(W.card)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
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
                    if (!state.configured) {
                        Body("打开 app 填入 API Key", color = W.dim)
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            buildBlocks(state).forEach { block ->
                                RingColumn(block, density, modifier = GlanceModifier.defaultWeight())
                            }
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
    private fun RingColumn(block: ProviderBlock, density: Float, modifier: GlanceModifier) {
        val ringPx = (RING_DP * density).toInt().coerceAtLeast(64)
        val arcs = buildList {
            val hero = block.heroPercent ?: 0
            add(hero to W.statusColor(block.status).toArgbCompat())
            if (block.providerId == "opencode") {
                block.rows.forEach { add(it.percent to W.statusColor(statusLevelOf(it.percent)).toArgbCompat()) }
            } else {
                block.rows.getOrNull(0)?.let { add(it.percent to W.statusColor(statusLevelOf(it.percent)).toArgbCompat()) }
            }
        }
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (block.errorMessage != null) {
                Text(
                    "同步失败",
                    style = TextStyle(color = ColorProvider(W.red), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(4.dp))
                Body(block.errorMessage.take(24), 8.sp, W.dim)
                return@Column
            }
            Box(
                modifier = GlanceModifier.size(RING_DP.dp).padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(
                        WidgetPaint.ringsBitmap(
                            sizePx = ringPx,
                            arcs = arcs,
                            strokePx = (3.5 * density).toInt().coerceAtLeast(3),
                            gapPx = (2 * density).toInt().coerceAtLeast(2),
                        )
                    ),
                    contentDescription = "${block.title} ${block.heroPercent ?: 0}%",
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${block.heroPercent ?: "—"}%",
                        style = TextStyle(
                            color = ColorProvider(W.ink),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Body(block.heroLabel, 8.sp, W.dim)
                }
            }
            Spacer(GlanceModifier.height(5.dp))
            Text(
                block.title,
                style = TextStyle(color = ColorProvider(W.label), fontSize = 10.sp, fontWeight = FontWeight.Medium),
            )
            val foot = block.rows.joinToString(" · ") { "${it.label.removePrefix("本月").removePrefix("月度")} ${it.percent}%" }
            if (foot.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Body(foot, 8.sp, W.dim)
            }
            block.footer?.let {
                Spacer(GlanceModifier.height(1.dp))
                Body(it, 8.sp, W.dim)
            }
        }
    }

    private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
        255,
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

    private companion object {
        const val RING_DP = 76
    }
}
