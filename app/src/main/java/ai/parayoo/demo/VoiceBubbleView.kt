package ai.parayoo.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.sin

class VoiceBubbleView(context: Context) : View(context) {
    enum class State { IDLE, LISTENING, PROCESSING }

    var state: State = State.IDLE
        set(value) {
            field = value
            if (value == State.LISTENING) startBarAnimation() else stopBarAnimation()
            invalidate()
        }

    var amplitude: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mic: Drawable = resources.getDrawable(R.drawable.ic_mic, context.theme)
    private var phase = 0f
    private var displayedAmplitude = 0f
    private var barAnimationRunning = false
    private val animateBars = object : Runnable {
        override fun run() {
            if (!barAnimationRunning) return
            displayedAmplitude += (amplitude - displayedAmplitude) * 0.58f
            phase += 0.34f
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val radius = width.coerceAtMost(height) / 2f
        val color = when (state) {
            State.IDLE -> Color.parseColor("#A78BFA")
            State.LISTENING -> Color.parseColor("#8B5CF6")
            State.PROCESSING -> Color.parseColor("#C4B5FD")
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)

        when (state) {
            State.IDLE -> drawMic(canvas)
            State.LISTENING -> drawVoiceBars(canvas)
            State.PROCESSING -> drawDots(canvas)
        }
    }

    private fun drawMic(canvas: Canvas) {
        val size = (width * 0.42f).toInt()
        val left = (width - size) / 2
        val top = (height - size) / 2
        mic.setBounds(left, top, left + size, top + size)
        mic.draw(canvas)
    }

    private fun drawVoiceBars(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width * 0.06f
        paint.color = Color.WHITE
        val centerY = height / 2f
        val restHalfHeight = height * 0.09f
        val voiceHalfHeight = height * 0.2f
        for (index in -1..1) {
            val variance = 1f + sin(phase + index * 1.9f).toFloat() * 0.12f
            val halfHeight = restHalfHeight + voiceHalfHeight * displayedAmplitude * variance
            val x = width / 2f + index * width * 0.18f
            canvas.drawLine(x, centerY - halfHeight, x, centerY + halfHeight, paint)
        }
    }

    private fun startBarAnimation() {
        if (barAnimationRunning) return
        barAnimationRunning = true
        postOnAnimation(animateBars)
    }

    private fun stopBarAnimation() {
        barAnimationRunning = false
        removeCallbacks(animateBars)
        displayedAmplitude = 0f
        amplitude = 0f
    }

    private fun drawDots(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val radius = width * 0.06f
        val center = width / 2f
        for (index in -1..1) {
            canvas.drawCircle(center + index * width * 0.18f, height / 2f, radius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        stopBarAnimation()
        super.onDetachedFromWindow()
    }
}
