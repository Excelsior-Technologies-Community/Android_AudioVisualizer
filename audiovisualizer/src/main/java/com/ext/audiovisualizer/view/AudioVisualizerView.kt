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

    /* =========================
       Paint & Config
       ========================= */

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var barColor = Color.GREEN
    private var barCount = 40
    private var barWidth = 8f

    /* =========================
       Audio & Visualizer
       ========================= */

    private var visualizer: Visualizer? = null

    // Smoothed audio buffer (single source of truth)
    private var smoothedData = FloatArray(128)
    private val smoothingFactor = 0.2f

    /* =========================
       Style
       ========================= */

    private var visualizerStyle = VisualizerStyle.BAR

    /* =========================
       Init
       ========================= */

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.AudioVisualizerView)

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

        val styleIndex = ta.getInt(
            R.styleable.AudioVisualizerView_av_style,
            0
        )
        visualizerStyle = VisualizerStyle.fromIndex(styleIndex)

        ta.recycle()

        paint.color = barColor
        paint.strokeWidth = barWidth
        paint.style = Paint.Style.STROKE
    }

    /* =========================
       Drawing
       ========================= */

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (visualizerStyle) {
            VisualizerStyle.BAR -> drawBars(canvas)
            VisualizerStyle.WAVE -> drawWave(canvas)
            VisualizerStyle.CIRCLE -> drawCircle(canvas)
            VisualizerStyle.STACKED_BARS -> drawStackedBars(canvas)
        }
    }

    /* =========================
       Public API
       ========================= */

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

    /* =========================
       Drawing Implementations
       ========================= */

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

    /* =========================
       Smoothing
       ========================= */

    private fun smoothAudioData(data: ByteArray) {
        val size = min(data.size, smoothedData.size)

        for (i in 0 until size) {
            val target = (data[i].toInt() and 0xFF).toFloat()
            smoothedData[i] += smoothingFactor * (target - smoothedData[i])
        }
    }
}
