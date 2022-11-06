package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.os.Handler
import com.github.penfeizhou.animation.executor.FrameDecoderExecutor
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.Writer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseFrameSeqDecoder<R : Reader, W : Writer> {
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
    protected abstract fun stop()

    fun getMemorySize(): Int {
        val frameBufferSizeBytes = frameBuffer?.capacity() ?: 0
        return bitmapPool.getMemorySize() + frameBufferSizeBytes
    }
}