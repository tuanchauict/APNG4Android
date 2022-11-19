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
import android.os.Message
import android.util.Log
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.github.penfeizhou.animation.decode.BaseFrameSeqDecoder
import com.github.penfeizhou.animation.decode.FrameSeqDecoder2
import com.github.penfeizhou.animation.decode.RenderListener
import com.github.penfeizhou.animation.loader.Loader
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

abstract class FrameAnimationDrawable : Drawable, Animatable2Compat, RenderListener {
    private val paint = Paint()
    val frameSeqDecoder: FrameSeqDecoder2
    private val drawFilter: DrawFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val animationCallbacks: MutableSet<Animatable2Compat.AnimationCallback> = HashSet()
    private var bitmap: Bitmap? = null
    private val uiHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_ANIMATION_START -> {
                    val callbacks = ArrayList(animationCallbacks)
                    for (animationCallback in callbacks) {
                        animationCallback.onAnimationStart(this@FrameAnimationDrawable)
                    }
                }
                MSG_ANIMATION_END -> {
                    val callbacks = ArrayList(animationCallbacks)
                    for (animationCallback in callbacks) {
                        animationCallback.onAnimationEnd(this@FrameAnimationDrawable)
                    }
                }
            }
        }
    }
    private val invalidateRunnable = Runnable { invalidateSelf() }
    private var autoPlay = true
    private val obtainedCallbacks: MutableSet<WeakReference<Callback?>> = HashSet()
    private var noMeasure = false

    constructor(frameSeqDecoder: FrameSeqDecoder2) {
        paint.isAntiAlias = true
        this.frameSeqDecoder = frameSeqDecoder
    }

    constructor(provider: Loader?) {
        paint.isAntiAlias = true
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
        if (bitmap != null && !bitmap!!.isRecycled) {
            bitmap!!.eraseColor(Color.TRANSPARENT)
        }
        frameSeqDecoder.reset()
    }

    fun pause() {
        frameSeqDecoder.pause()
    }

    fun resume() {
        frameSeqDecoder.resume()
    }

    val isPaused: Boolean
        get() = frameSeqDecoder.isPaused()

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
        if (autoPlay) {
            frameSeqDecoder.start()
        } else {
            if (!frameSeqDecoder.isRunning) {
                frameSeqDecoder.start()
            }
        }
    }

    override fun stop() {
        innerStop()
    }

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

    override fun isRunning(): Boolean {
        return frameSeqDecoder.isRunning
    }

    override fun draw(canvas: Canvas) {
        if (bitmap == null || bitmap!!.isRecycled) {
            return
        }
        canvas.drawFilter = drawFilter
        canvas.drawBitmap(bitmap!!, matrix, paint)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        val sampleSizeChanged = frameSeqDecoder.setDesiredSize(bounds.width(), bounds.height())
        val sampleSize = frameSeqDecoder.sampleSize
        matrix.setScale(
            1.0f * bounds.width() * sampleSize / frameSeqDecoder.getBounds().width(),
            1.0f * bounds.height() * sampleSize / frameSeqDecoder.getBounds().height()
        )
        if (sampleSizeChanged) bitmap = Bitmap.createBitmap(
            frameSeqDecoder.getBounds().width() / sampleSize,
            frameSeqDecoder.getBounds().height() / sampleSize,
            Bitmap.Config.ARGB_8888
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun onStart() {
        Message.obtain(uiHandler, MSG_ANIMATION_START).sendToTarget()
    }

    override fun onRender(byteBuffer: ByteBuffer) {
        if (!isRunning) {
            return
        }
        if (bitmap == null || bitmap!!.isRecycled) {
            val sampleSize = frameSeqDecoder.sampleSize
            bitmap = Bitmap.createBitmap(
                frameSeqDecoder.getBounds().width() / sampleSize,
                frameSeqDecoder.getBounds().height() / sampleSize,
                Bitmap.Config.ARGB_8888
            )
        }
        byteBuffer.rewind()
        if (byteBuffer.remaining() < bitmap!!.byteCount) {
            Log.e(TAG, "onRender:Buffer not large enough for pixels")
            return
        }
        bitmap!!.copyPixelsFromBuffer(byteBuffer)
        uiHandler.post(invalidateRunnable)
    }

    override fun onEnd() {
        Message.obtain(uiHandler, MSG_ANIMATION_END).sendToTarget()
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

    override fun getIntrinsicWidth(): Int {
        return if (noMeasure) {
            -1
        } else try {
            frameSeqDecoder.getBounds().width()
        } catch (exception: Exception) {
            0
        }
    }

    override fun getIntrinsicHeight(): Int {
        return if (noMeasure) {
            -1
        } else try {
            frameSeqDecoder.getBounds().height()
        } catch (exception: Exception) {
            0
        }
    }

    override fun registerAnimationCallback(animationCallback: Animatable2Compat.AnimationCallback) {
        animationCallbacks.add(animationCallback)
    }

    override fun unregisterAnimationCallback(animationCallback: Animatable2Compat.AnimationCallback): Boolean {
        return animationCallbacks.remove(animationCallback)
    }

    override fun clearAnimationCallbacks() {
        animationCallbacks.clear()
    }

    val memorySize: Int
        get() {
            var size = frameSeqDecoder.getMemorySize()
            if (bitmap != null && !bitmap!!.isRecycled) {
                size += bitmap!!.allocationByteCount
            }
            return Math.max(1, size)
        }

    override fun getCallback(): Callback? {
        return super.getCallback()
    }

    private fun hookRecordCallbacks() {
        val lost: MutableList<WeakReference<Callback?>> = ArrayList()
        val callback = callback
        var recorded = false
        val temp: Set<WeakReference<Callback?>> = HashSet(obtainedCallbacks)
        for (ref in temp) {
            val cb = ref.get()
            if (cb == null) {
                lost.add(ref)
            } else {
                if (cb === callback) {
                    recorded = true
                } else {
                    cb.invalidateDrawable(this)
                }
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
        private const val MSG_ANIMATION_START = 1
        private const val MSG_ANIMATION_END = 2
    }
}