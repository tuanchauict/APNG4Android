package com.github.penfeizhou.animation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DrawFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.github.penfeizhou.animation.decode.BaseFrameSeqDecoder
import com.github.penfeizhou.animation.decode.FrameSeqDecoder2
import com.github.penfeizhou.animation.decode.RenderListener
import com.github.penfeizhou.animation.loader.Loader
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import kotlin.math.max

abstract class FrameAnimationDrawable : Drawable, Animatable2Compat, RenderListener {
    private val paint = Paint().apply { isAntiAlias = true }
    val frameSeqDecoder: FrameSeqDecoder2
    private val drawFilter: DrawFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val animationCallbacks = mutableSetOf<Animatable2Compat.AnimationCallback>()

    private var bitmap: Bitmap? = null
    private val unrecycledBitmap: Bitmap?
        get() = bitmap?.takeUnless { it.isRecycled }

    private val uiHandler: Handler = Handler(Looper.getMainLooper())
    private val invalidateRunnable = Runnable { invalidateSelf() }
    private var autoPlay = true
    private val obtainedCallbacks: MutableSet<WeakReference<Callback?>> = HashSet()
    private var noMeasure = false

    val isPaused: Boolean
        get() = frameSeqDecoder.isPaused()

    val memorySize: Int
        get() {
            val bitmapSize = unrecycledBitmap?.allocationByteCount ?: 0
            return max(frameSeqDecoder.getMemorySize() + bitmapSize, 1)
        }

    constructor(frameSeqDecoder: FrameSeqDecoder2) {
        this.frameSeqDecoder = frameSeqDecoder
    }

    constructor(provider: Loader?) {
        frameSeqDecoder = createFrameSeqDecoder(provider, this)
    }

    fun setAutoPlay(autoPlay: Boolean) {
        this.autoPlay = autoPlay
    }

    fun setNoMeasure(noMeasure: Boolean) {
        this.noMeasure = noMeasure
    }

    protected abstract fun createFrameSeqDecoder(
        streamLoader: Loader?,
        listener: RenderListener?
    ): FrameSeqDecoder2

    /**
     * @param loopLimit <=0为无限播放,>0为实际播放次数
     */
    fun setLoopLimit(loopLimit: Int) {
        frameSeqDecoder.setLoopLimit(loopLimit)
    }

    fun reset() {
        unrecycledBitmap?.eraseColor(Color.TRANSPARENT)
        frameSeqDecoder.reset()
    }

    fun pause() {
        frameSeqDecoder.pause()
    }

    fun resume() {
        frameSeqDecoder.resume()
    }

    override fun start() {
        if (frameSeqDecoder.isRunning) {
            frameSeqDecoder.stop()
        }
        frameSeqDecoder.reset()
        innerStart()
    }

    private fun innerStart() {
        if (BaseFrameSeqDecoder.DEBUG) {
            Log.d(TAG, "$this,start")
        }
        frameSeqDecoder.addRenderListener(this)
        if (autoPlay || !frameSeqDecoder.isRunning) {
            frameSeqDecoder.start()
        }
    }

    override fun stop() = innerStop()

    private fun innerStop() {
        if (BaseFrameSeqDecoder.DEBUG) {
            Log.d(TAG, "$this,stop")
        }
        frameSeqDecoder.removeRenderListener(this)
        if (autoPlay) {
            frameSeqDecoder.stop()
        } else {
            frameSeqDecoder.stopIfNeeded()
        }
    }

    override fun isRunning(): Boolean = frameSeqDecoder.isRunning

    override fun draw(canvas: Canvas) {
        val bitmap = unrecycledBitmap ?: return
        canvas.drawFilter = drawFilter
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)

        val isSampleSizeChanged = frameSeqDecoder.setDesiredSize(bounds.width(), bounds.height())
        val sampleSize = frameSeqDecoder.sampleSize
        val viewport = frameSeqDecoder.getViewport()
        matrix.setScale(
            1.0f * bounds.width() * sampleSize / viewport.width,
            1.0f * bounds.height() * sampleSize / viewport.height
        )
        if (isSampleSizeChanged) {
            bitmap?.recycle()
            bitmap = frameSeqDecoder.createBitmap()
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onStart() {
        for (animationCallback in animationCallbacks.asIterable()) {
            animationCallback.onAnimationStart(this@FrameAnimationDrawable)
        }
    }

    override fun onEnd() {
        uiHandler.post {
            for (animationCallback in animationCallbacks.asIterable()) {
                animationCallback.onAnimationEnd(this@FrameAnimationDrawable)
            }
        }
    }

    override fun onRender(byteBuffer: ByteBuffer) {
        if (!isRunning) {
            return
        }
        val bitmap = unrecycledBitmap ?: frameSeqDecoder.createBitmap()
        this.bitmap = bitmap

        byteBuffer.rewind()
        if (byteBuffer.remaining() < bitmap.byteCount) {
            Log.e(TAG, "onRender:Buffer not large enough for pixels")
            return
        }
        bitmap.copyPixelsFromBuffer(byteBuffer)
        uiHandler.post(invalidateRunnable)
    }

    private fun FrameSeqDecoder2.createBitmap(): Bitmap {
        val viewport = getViewport()
        return Bitmap.createBitmap(
            viewport.width / sampleSize,
            viewport.height / sampleSize,
            Bitmap.Config.ARGB_8888
        )
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        hookRecordCallbacks()
        if (autoPlay) {
            if (BaseFrameSeqDecoder.DEBUG) {
                Log.d(TAG, "$this,visible:$visible,restart:$restart")
            }
            if (visible) {
                if (!isRunning) {
                    innerStart()
                }
            } else if (isRunning) {
                innerStop()
            }
        }
        return super.setVisible(visible, restart)
    }

    override fun getIntrinsicWidth(): Int =
        if (noMeasure) {
            -1
        } else {
            try {
                frameSeqDecoder.getViewport().width
            } catch (_: Exception) {
                0
            }
        }

    override fun getIntrinsicHeight(): Int = if (noMeasure) {
        -1
    } else {
        try {
            frameSeqDecoder.getViewport().height
        } catch (_: Exception) {
            0
        }
    }

    override fun registerAnimationCallback(animationCallback: Animatable2Compat.AnimationCallback) {
        animationCallbacks.add(animationCallback)
    }

    override fun unregisterAnimationCallback(
        animationCallback: Animatable2Compat.AnimationCallback
    ): Boolean = animationCallbacks.remove(animationCallback)

    override fun clearAnimationCallbacks() = animationCallbacks.clear()

    private fun hookRecordCallbacks() {
        val lost: MutableList<WeakReference<Callback?>> = ArrayList()
        val callback = callback
        var recorded = false
        val temp: Set<WeakReference<Callback?>> = HashSet(obtainedCallbacks)
        for (ref in temp) {
            val cb = ref.get()
            when {
                cb == null -> lost.add(ref)
                cb === callback -> recorded = true
                else -> cb.invalidateDrawable(this)
            }
        }
        for (ref in lost) {
            obtainedCallbacks.remove(ref)
        }
        if (!recorded) {
            obtainedCallbacks.add(WeakReference(callback))
        }
    }

    override fun invalidateSelf() {
        super.invalidateSelf()
        val temp: Set<WeakReference<Callback?>> = HashSet(obtainedCallbacks)
        for (ref in temp) {
            val callback = ref.get()
            if (callback != null && callback !== getCallback()) {
                callback.invalidateDrawable(this)
            }
        }
    }

    companion object {
        private val TAG = FrameAnimationDrawable::class.java.simpleName
    }
}