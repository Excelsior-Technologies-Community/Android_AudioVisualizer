package com.ext.audiovisualizer.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import com.ext.audiovisualizer.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var barColor = Color.GREEN
    private var barCount = 40
    private var barWidth = 8f
    private var visualizer: Visualizer? = null
    private var smoothedData = FloatArray(128)
    private val smoothingFactor = 0.2f
    private var visualizerStyle = VisualizerStyle.BAR

    private var enableGradient = false
    private var gradientStartColor = Color.CYAN
    private var gradientEndColor = Color.MAGENTA

    private var enableGlow = false
    private var glowRadius = 20f


    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.AudioVisualizerView)

        // Basic config
        barColor = ta.getColor(
            R.styleable.AudioVisualizerView_av_barColor,
            barColor
        )

        barCount = ta.getInt(
            R.styleable.AudioVisualizerView_av_barCount,
            barCount
        )

        barWidth = ta.getDimension(
            R.styleable.AudioVisualizerView_av_barWidth,
            barWidth
        )

        // Style
        val styleIndex = ta.getInt(
            R.styleable.AudioVisualizerView_av_style,
            0
        )
        visualizerStyle = VisualizerStyle.fromIndex(styleIndex)

        // ✅ Gradient
        enableGradient = ta.getBoolean(
            R.styleable.AudioVisualizerView_av_enableGradient,
            false
        )

        gradientStartColor = ta.getColor(
            R.styleable.AudioVisualizerView_av_gradientStartColor,
            barColor
        )

        gradientEndColor = ta.getColor(
            R.styleable.AudioVisualizerView_av_gradientEndColor,
            barColor
        )

        // ✅ Glow
        enableGlow = ta.getBoolean(
            R.styleable.AudioVisualizerView_av_enableGlow,
            false
        )

        glowRadius = ta.getDimension(
            R.styleable.AudioVisualizerView_av_glowRadius,
            20f
        )

        // ✅ Recycle ONLY AT THE END
        ta.recycle()

        // Paint config
        paint.color = barColor
        paint.strokeWidth = barWidth
        paint.style = Paint.Style.STROKE
    }



    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        applyGradient()
        applyGlow()

        when (visualizerStyle) {
            VisualizerStyle.BAR -> drawBars(canvas)
            VisualizerStyle.WAVE -> drawWave(canvas)
            VisualizerStyle.CIRCLE -> drawCircle(canvas)
            VisualizerStyle.STACKED_BARS -> drawStackedBars(canvas)
        }
    }



    fun setAudioSessionId(sessionId: Int) {
        release()

        visualizer = Visualizer(sessionId).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1]

            setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {

                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform?.let {
                            smoothAudioData(it)
                            invalidate()
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        // Not used for now
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )

            enabled = true
        }
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        visualizerStyle = style
        invalidate()
    }

    fun setBarColor(color: Int) {
        barColor = color
        paint.color = barColor
        invalidate()
    }

    fun setBarCount(count: Int) {
        barCount = count.coerceAtLeast(1)
        invalidate()
    }

    fun release() {
        visualizer?.release()
        visualizer = null
    }

    private fun drawBars(canvas: Canvas) {
        val barSpacing = width / barCount.toFloat()
        val size = min(barCount, smoothedData.size)

        for (i in 0 until size) {
            val value = smoothedData[i]
            val barHeight = value / 255f * height
            val x = i * barSpacing

            canvas.drawLine(
                x,
                height.toFloat(),
                x,
                height - barHeight,
                paint
            )
        }
    }

    private fun drawWave(canvas: Canvas) {
        val midY = height / 2f
        val gap = width / smoothedData.size.toFloat()

        for (i in 1 until smoothedData.size) {
            val x1 = (i - 1) * gap
            val y1 = midY + (smoothedData[i - 1] - 128f) * midY / 128f
            val x2 = i * gap
            val y2 = midY + (smoothedData[i] - 128f) * midY / 128f

            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    private fun drawStackedBars(canvas: Canvas) {
        val barWidth = width / barCount.toFloat()
        val size = min(barCount, smoothedData.size)

        for (i in 0 until size) {
            val value = smoothedData[i].toInt()
            val stacks = value / 20

            for (j in 0 until stacks) {
                val top = height - j * 20f
                canvas.drawRect(
                    i * barWidth,
                    top - 15f,
                    i * barWidth + barWidth - 4f,
                    top,
                    paint
                )
            }
        }
    }

    private fun drawCircle(canvas: Canvas) {
        val radius = min(width, height) / 4f
        val cx = width / 2f
        val cy = height / 2f

        for (i in smoothedData.indices step 8) {
            val value = smoothedData[i]
            val angle = i * 360f / smoothedData.size
            val length = radius + value

            val x = (cx + length * cos(Math.toRadians(angle.toDouble()))).toFloat()
            val y = (cy + length * sin(Math.toRadians(angle.toDouble()))).toFloat()

            canvas.drawLine(cx, cy, x, y, paint)
        }
    }


    private fun smoothAudioData(data: ByteArray) {
        val size = min(data.size, smoothedData.size)

        for (i in 0 until size) {
            val target = (data[i].toInt() and 0xFF).toFloat()
            smoothedData[i] += smoothingFactor * (target - smoothedData[i])
        }
    }

    private fun applyGradient() {
        if (!enableGradient) {
            paint.shader = null
            return
        }

        paint.shader = when (visualizerStyle) {

            VisualizerStyle.CIRCLE -> {
                android.graphics.RadialGradient(
                    width / 2f,
                    height / 2f,
                    min(width, height) / 2f,
                    gradientStartColor,
                    gradientEndColor,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }

            else -> {
                android.graphics.LinearGradient(
                    0f,
                    height.toFloat(),
                    0f,
                    0f,
                    gradientStartColor,
                    gradientEndColor,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
        }
    }

    private fun applyGlow() {
        if (enableGlow) {
            setLayerType(LAYER_TYPE_SOFTWARE, paint)
            paint.maskFilter =
                android.graphics.BlurMaskFilter(glowRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        } else {
            paint.maskFilter = null
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    fun enableGradient(
        startColor: Int,
        endColor: Int
    ) {
        enableGradient = true
        gradientStartColor = startColor
        gradientEndColor = endColor
        invalidate()
    }

    fun disableGradient() {
        enableGradient = false
        invalidate()
    }

    fun enableGlow(radius: Float = 20f) {
        enableGlow = true
        glowRadius = radius
        invalidate()
    }

    fun disableGlow() {
        enableGlow = false
        invalidate()
    }


}
