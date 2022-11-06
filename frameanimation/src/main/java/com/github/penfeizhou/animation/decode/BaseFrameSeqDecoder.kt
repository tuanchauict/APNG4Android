package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.github.penfeizhou.animation.decode.FrameSeqDecoder.RenderListener
import com.github.penfeizhou.animation.executor.FrameDecoderExecutor
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.Writer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseFrameSeqDecoder<R : Reader, W : Writer>(
    renderListener: RenderListener?
) {
    @JvmField
    protected var frameBuffer: ByteBuffer? = null

    @JvmField
    protected var frames: MutableList<Frame<R, W>> = mutableListOf()

    @JvmField
    protected var frameIndex = -1

    @JvmField
    internal val paused = AtomicBoolean(true)

    @JvmField
    internal val workerHandler = Handler(
        FrameDecoderExecutor.getInstance()
            .getLooper(FrameDecoderExecutor.getInstance().generateTaskId())
    )

    @JvmField
    protected val renderListeners: MutableSet<RenderListener> =
        listOfNotNull(renderListener).toMutableSet()

    @JvmField
    protected val renderTask: Runnable = RenderTaskRunnable()

    private val bitmapPool = BitmapPool()


    /**
     * Obtains a bitmap with size [width] x [height] with [Bitmap.Config.ARGB_8888] config.
     *
     * First, try to reuse a bitmap from the pool. If there is no bitmap in the pool which has size
     * equal or larger than [width]x[height], create a new bitmap.
     * Return null if either [width] or [height] is invalid (<= 0)
     */
    protected fun obtainBitmap(width: Int, height: Int): Bitmap? = bitmapPool.obtain(width, height)

    protected fun recycleBitmap(bitmap: Bitmap?) = bitmapPool.recycle(bitmap)

    protected fun clearBitmapPool() = bitmapPool.clear()

    protected abstract fun canStep(): Boolean
    protected abstract fun step(): Long
    protected abstract fun stop()

    fun addRenderListener(listener: RenderListener) {
        ensureWorkerExecute { renderListeners.add(listener) }
    }

    fun removeRenderListener(listener: RenderListener) {
        ensureWorkerExecute { renderListeners.remove(listener) }
    }

    fun getMemorySize(): Int {
        val frameBufferSizeBytes = frameBuffer?.capacity() ?: 0
        return bitmapPool.getMemorySize() + frameBufferSizeBytes
    }

    protected fun ensureWorkerExecute(block: () -> Unit) {
        if (Looper.myLooper() == workerHandler.looper) {
            block()
        } else {
            workerHandler.post(block)
        }
    }

    private inner class RenderTaskRunnable(
        private val currentTimeProvider: () -> Long = System::currentTimeMillis
    ) : Runnable {
        override fun run() {
            if (DEBUG) {
                Log.d(TAG, "$this#run")
            }

            if (paused.get()) {
                return
            }

            if (!canStep()) {
                stop()
                return
            }

            val start = currentTimeProvider.invoke()
            val delay = step()
            val cost = currentTimeProvider.invoke() - start

            workerHandler.postDelayed(this, (delay - cost).coerceAtLeast(0))

            val frameBuffer = frameBuffer ?: return
            for (listener in renderListeners) {
                listener.onRender(frameBuffer)
            }
        }
    }

    companion object {
        const val DEBUG = false

        private const val TAG = "FrameDecoder"
    }
}