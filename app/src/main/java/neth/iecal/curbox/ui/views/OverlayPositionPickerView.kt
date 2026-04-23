package neth.iecal.curbox.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class OverlayPositionPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var positionX: Float = 0.5f
        private set
    var positionY: Float = 0.05f
        private set

    var onPositionChanged: ((Float, Float) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E1E")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        strokeWidth = 1f
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#83D5C5")
    }
    private val indicatorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bgRect = RectF()
    private val cornerRadius = 16f
    private val indicatorRadius = 18f

    fun setPosition(x: Float, y: Float) {
        positionX = x.coerceIn(0f, 1f)
        positionY = y.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        for (i in 1..2) {
            val x = width * i / 3f
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            val y = height * i / 3f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        val cx = positionX * width
        val cy = positionY * height
        canvas.drawCircle(cx, cy, indicatorRadius, indicatorPaint)
        canvas.drawCircle(cx, cy, indicatorRadius, indicatorBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                positionX = (event.x / width).coerceIn(0f, 1f)
                positionY = (event.y / height).coerceIn(0f, 1f)
                onPositionChanged?.invoke(positionX, positionY)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
