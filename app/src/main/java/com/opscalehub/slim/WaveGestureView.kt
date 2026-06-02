package com.opscalehub.slim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.exp

class WaveGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnLetterSelectedListener {
        fun onLetterSelected(letter: String)
        fun onLetterReleased()
    }

    var listener: OnLetterSelectedListener? = null

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("")
        .filter { it.isNotEmpty() }

    private var activeIndex = -1
    private var isDragging = false
    private var touchY = 0f

    // Pre-allocated styling paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748b") // text_muted
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    private val textBounds = Rect()
    
    // Haptics system
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (height == 0) return

        val itemHeight = height.toFloat() / alphabet.size
        val centerX = width / 2f

        for (i in alphabet.indices) {
            val letter = alphabet[i]
            val itemCenterY = (i * itemHeight) + (itemHeight / 2f)

            var displacementX = 0f
            var scale = 1.0f

            if (isDragging) {
                val dy = itemCenterY - touchY
                val maxDisplacement = -30f * resources.displayMetrics.density // dp to pixels
                val sigma = 40f * resources.displayMetrics.density // Gaussian width spread

                // Apply Gaussian formula
                val factor = exp(-(dy * dy) / (2 * sigma * sigma))
                displacementX = maxDisplacement * factor
                scale = 1.0f + 0.6f * factor
            }

            // Configure Paint dynamically
            if (i == activeIndex) {
                textPaint.color = Color.parseColor("#f8fafc") // text_primary
                textPaint.isFakeBoldText = true
            } else {
                textPaint.color = Color.parseColor("#64748b") // text_muted
                textPaint.isFakeBoldText = false
            }

            val currentTextSize = 10f * resources.displayMetrics.density * scale
            textPaint.textSize = currentTextSize

            // Center text vertically
            textPaint.getTextBounds(letter, 0, letter.length, textBounds)
            val textY = itemCenterY + (textBounds.height() / 2f)

            canvas.drawText(letter, centerX + displacementX, textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val itemHeight = height.toFloat() / alphabet.size
        touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isDragging = true
                val index = (touchY / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                
                if (index != activeIndex) {
                    activeIndex = index
                    triggerTick()
                    listener?.onLetterSelected(alphabet[activeIndex])
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                activeIndex = -1
                listener?.onLetterReleased()
                invalidate()
            }
        }
        return true
    }

    private fun triggerTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }
}
