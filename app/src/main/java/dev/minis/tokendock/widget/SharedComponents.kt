package dev.minis.tokendock.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionRunCallback
import androidx.glance.appwidget.CircleIconButton
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.minis.tokendock.R

/** 设计 Token：全组件共用的颜色/字号 */
object W {
    val ink = Color(0xFFF5F6F8)        // 数字主白
    val label = Color(0xFF8A8F9A)      // 主灰
    val dim = Color(0xFF565A63)        // 深灰
    val brand = Color(0xFFE8C36A)      // 品牌金
    val card = Color(0xFF17191E)       // 卡片底
    val track = Color(0xFF26282E)      // 进度条底
    val green = Color(0xFF34D399)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFF87171)

    fun statusColor(level: StatusLevel): Color = when (level) {
        StatusLevel.OK -> green
        StatusLevel.WARN -> amber
        StatusLevel.ERROR -> red
        StatusLevel.PENDING -> label
    }
}

/** 状态点：8dp 圆点，颜色即语义 */
@Composable
fun StatusDot(level: StatusLevel, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier = modifier.size(8.dp).cornerRadius(4.dp).background(W.statusColor(level)))
}

/**
 * 右上角刷新按钮：纯图标圆形，同步中变灰。
 * 视觉 40dp。CircleIconButton 用 disabled 状态表达"同步中"。
 */
@Composable
fun RefreshIconButton(refreshing: Boolean, modifier: GlanceModifier = GlanceModifier) {
    CircleIconButton(
        imageProvider = ImageProvider(
            if (refreshing) R.drawable.ic_refresh_dim else R.drawable.ic_refresh
        ),
        contentDescription = if (refreshing) "同步中" else "刷新额度",
        onClick = actionRunCallback<RefreshAction>(),
        modifier = modifier,
        enabled = !refreshing,
        backgroundColor = ColorProvider(W.card),
        contentColor = ColorProvider(if (refreshing) W.dim else W.brand),
    )
}

/** 发丝分割线 */
@Composable
fun HairLine(modifier: GlanceModifier = GlanceModifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(W.track))
}

/** 区块标题（金色小字） */
@Composable
fun Title(text: String, size: TextUnit = 11.sp, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text,
        style = TextStyle(color = ColorProvider(W.brand), fontSize = size, fontWeight = FontWeight.Medium),
        modifier = modifier,
    )
}

/** 正文灰字 */
@Composable
fun Body(
    text: String,
    size: TextUnit = 10.sp,
    color: Color = W.label,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text,
        style = TextStyle(color = ColorProvider(color), fontSize = size),
        modifier = modifier,
    )
}
