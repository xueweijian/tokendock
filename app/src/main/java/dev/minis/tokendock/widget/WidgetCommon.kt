package dev.minis.tokendock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll

/** 三个组件统一刷新入口（SyncEngine 调用） */
suspend fun refreshAllWidgets(context: Context) {
    HeroWidget().updateAll(context)
    RingWidget().updateAll(context)
    TableWidget().updateAll(context)
}

/** 共享绘制工具：Canvas 位图渲染（圆环 / 圆角条用） */
object WidgetPaint {

    /** 语义色（与 UiModels.StatusLevel 对应） */
    fun colorOf(level: StatusLevel): Int = when (level) {
        StatusLevel.OK -> 0xFF34D399.toInt()
        StatusLevel.WARN -> 0xFFFBBF24.toInt()
        StatusLevel.ERROR -> 0xFFF87171.toInt()
        StatusLevel.PENDING -> 0xFF8A8F9A.toInt()
    }

    /**
     * Fitness-rings 式三段弧圆环位图。
     * [arcs] 从外到内: (percent, colorArgb)；半径由外环到内环递减 [stroke]。
     */
    fun ringsBitmap(
        sizePx: Int,
        arcs: List<Pair<Int, Int>>,
        strokePx: Int,
        gapPx: Int,
        trackColor: Int = 0xFF26282E.toInt(),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx.toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        arcs.forEachIndexed { i, (percent, color) ->
            val inset = (strokePx + gapPx) * i + strokePx / 2f
            val rect = RectF(
                inset,
                inset,
                sizePx - inset,
                sizePx - inset,
            )
            // track
            paint.color = trackColor
            canvas.drawArc(rect, 0f, 360f, false, paint)
            // arc
            paint.color = color
            canvas.drawArc(rect, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, paint)
        }
        return bmp
    }
}

/** 供 Glance id 复用的基类：三个组件共享"读状态 → provideContent"骨架 */
abstract class BaseQuotaWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    protected fun widgetIdTag(): String = this.javaClass.simpleName
}

/** 类型标记，避免反射 */
object WidgetRefs {
    val all: List<GlanceAppWidget> get() = listOf(HeroWidget(), RingWidget(), TableWidget())
}
