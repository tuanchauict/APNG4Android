package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.WorkerThread
import com.github.penfeizhou.animation.decode.FrameSeqDecoder.RenderListener
import com.github.penfeizhou.animation.executor.FrameDecoderExecutor
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseFrameSeqDecoder<R : Reader, W : Writer>(
    protected val loader: Loader,
    renderListener: RenderListener?,
    readerFactory: (Reader) -> R
) {
    @JvmField
    protected var frameBuffer: ByteBuffer? = null

    @JvmField
    protected var frames: MutableList<Frame<R, W>> = mutableListOf()
    val frameCount: Int
        get() = frames.size

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
    private val bitmapReaderManager = BitmapReaderManager(loader, readerFactory)

    @JvmField
    protected var playCount: Int = 0

    @JvmField
    protected var loopLimit: Int? = null

    /**
     * If played all the needed
     */
    @JvmField
    protected var finished: Boolean = false

    @Volatile
    protected var mState = State.IDLE

    @JvmField
    @Volatile
    protected var fullRect: Rect? = null

    @JvmField
    var sampleSize = 1

    val isRunning: Boolean
        get() = mState == State.RUNNING || mState == State.INITIALIZING

    private val debugInfo: String
        get() = if (DEBUG) {
            "Thread is ${Thread.currentThread()}, decoder is $this, state is $mState"
        } else {
            ""
        }

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

    protected fun canStep(): Boolean {
        if (!isRunning || frames.isEmpty()) {
            return false
        }
        val numPlays = getNumPlays()
        if (numPlays <= 0) {
            return true
        }

        if (playCount < numPlays - 1) {
            return true
        } else if (playCount == numPlays - 1 && frameIndex < frames.lastIndex) {
            return true
        }
        finished = true
        return false
    }

    /**
     * Prepares a step during animating.
     * Returns the duration of the frame.
     */
    @WorkerThread
    protected fun step(): Long {
        frameIndex += 1
        if (frameIndex >= frames.size) {
            frameIndex = 0
            playCount += 1
        }
        val frame = getFrame(frameIndex) ?: return 0
        renderFrame(frame)
        return frame.frameDuration.toLong()
    }

    fun start() {
        if (fullRect == RECT_EMPTY) {
            return
        }

        if (isRunning) {
            Log.i(TAG, "$debugInfo Already started")
            return
        }

        if (mState == State.FINISHING) {
            Log.e(TAG, "$debugInfo Processing, wait for finish at $mState")
        }
        if (DEBUG) {
            Log.i(TAG, "$debugInfo Set state to INITIALIZING")
        }

        mState = State.INITIALIZING
        ensureWorkerExecute(::innerStart)
    }

    @WorkerThread
    protected fun innerStart() {
        paused.compareAndSet(true, false)
        val startTimeMillis = System.currentTimeMillis()

        if (frames.isEmpty()) {
            try {
                initCanvasBounds(read())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        Log.i(
            TAG,
            """$debugInfo 
                |Set state to running 
                |cost = ${System.currentTimeMillis() - startTimeMillis}""".trimMargin()
        )
        mState = State.RUNNING

        if (getNumPlays() == 0 || !finished) {
            frameIndex = -1
            renderTask.run()

            for (listener in renderListeners) {
                listener.onStart()
            }
        } else {
            Log.i(TAG, "$debugInfo No need to start")
        }
    }

    protected abstract fun stop()

    fun stopIfNeeded() = ensureWorkerExecute {
        if (renderListeners.isEmpty()) {
            stop()
        }
    }

    fun resume() {
        paused.compareAndSet(true, false)
        workerHandler.removeCallbacks(renderTask)
        workerHandler.post(renderTask)
    }

    fun pause() {
        workerHandler.removeCallbacks(renderTask)
        paused.compareAndSet(false, true)
    }

    fun reset() = ensureWorkerExecute {
        playCount = 0
        frameIndex = -1
        finished = false
    }

    fun isPaused(): Boolean = paused.get()

    fun addRenderListener(listener: RenderListener) =
        ensureWorkerExecute { renderListeners.add(listener) }

    fun removeRenderListener(listener: RenderListener) =
        ensureWorkerExecute { renderListeners.remove(listener) }

    @WorkerThread
    protected abstract fun renderFrame(frame: Frame<R, W>)

    fun getFrame(index: Int): Frame<R, W>? = frames.getOrNull(index)

    @WorkerThread
    protected fun closeReader() = bitmapReaderManager.closeReader()

    protected fun initCanvasBounds(rect: Rect) {
        fullRect = rect
        val capacityBytes = (rect.width() * rect.height() / (sampleSize * sampleSize) + 1) * 4
        frameBuffer = ByteBuffer.allocate(capacityBytes)
    }

    @Throws(IOException::class)
    protected fun read(): Rect = read(bitmapReaderManager.getReader())

    @Throws(IOException::class)
    protected abstract fun read(reader: R): Rect

    fun getMemorySize(): Int {
        val frameBufferSizeBytes = frameBuffer?.capacity() ?: 0
        return bitmapPool.getMemorySize() + frameBufferSizeBytes
    }

    fun setLoopLimit(limit: Int) {
        loopLimit = limit
    }

    protected fun getNumPlays(): Int = loopLimit ?: getLoopCount()

    /**
     * Gets the Loop Count defined in file
     */
    protected abstract fun getLoopCount(): Int

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

    protected enum class State {
        IDLE, RUNNING, INITIALIZING, FINISHING
    }

    companion object {
        const val DEBUG = false
        val RECT_EMPTY = Rect()

        private const val TAG = "FrameDecoder"
    }
}