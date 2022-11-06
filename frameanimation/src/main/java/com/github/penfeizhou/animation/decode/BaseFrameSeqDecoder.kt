package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.WorkerThread
import com.github.penfeizhou.animation.executor.FrameDecoderExecutor
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

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

    private val renderListeners: MutableSet<RenderListener> =
        listOfNotNull(renderListener).toMutableSet()

    @JvmField
    protected val renderTask: Runnable = RenderTaskRunnable()

    private val bitmapPool = BitmapPool()
    private val bitmapReaderManager = BitmapReaderManager(loader, readerFactory)

    @JvmField
    protected val cachedCanvas: MutableMap<Bitmap, Canvas> = WeakHashMap()

    private var playCount: Int = 0

    private var loopLimit: Int? = null

    private val numPlays: Int
        get() = loopLimit ?: getLoopCount()

    /**
     * If played all the needed
     */
    private var finished: Boolean = false

    @Volatile
    protected var state = State.IDLE
        set(value) {
            field = value
            if (DEBUG) {
                Log.i(TAG, "$debugInfo Update state to $value")
            }
        }

    @JvmField
    @Volatile
    protected var fullRect: Rect? = null

    @JvmField
    var sampleSize = 1

    val isRunning: Boolean
        get() = state == State.RUNNING || state == State.INITIALIZING

    private val debugInfo: String
        get() = if (DEBUG) {
            "Thread is ${Thread.currentThread()}, decoder is $this, state is $state"
        } else {
            ""
        }

    fun getBounds(): Rect {
        if (fullRect == null) {
            if (state == State.FINISHING) {
                Log.e(TAG, " in finishing. Do not interrupt")
            }
            val thread = Thread.currentThread()
            ensureWorkerExecute {
                try {
                    if (fullRect == null) {
                        initCanvasBounds()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    fullRect = RECT_EMPTY
                } finally {
                    LockSupport.unpark(thread)
                }
            }
            LockSupport.park(thread)
        }

        return fullRect ?: RECT_EMPTY
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

    protected fun canStep(): Boolean {
        if (!isRunning || frames.isEmpty()) {
            return false
        }
        val numPlays = numPlays
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

        if (state == State.FINISHING) {
            Log.e(TAG, "$debugInfo Processing, wait for finish at $state")
        }

        state = State.INITIALIZING
        ensureWorkerExecute(::innerStart)
    }

    @WorkerThread
    internal fun innerStart() {
        paused.compareAndSet(true, false)
        val startTimeMillis = System.currentTimeMillis()

        if (frames.isEmpty()) {
            try {
                initCanvasBounds()
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
        state = State.RUNNING

        if (numPlays == 0 || !finished) {
            frameIndex = -1
            renderTask.run()

            for (listener in renderListeners) {
                listener.onStart()
            }
        } else {
            Log.i(TAG, "$debugInfo No need to start")
        }
    }

    fun stopIfNeeded() = ensureWorkerExecute {
        if (renderListeners.isEmpty()) {
            stop()
        }
    }

    fun stop() {
        if (fullRect == RECT_EMPTY) {
            return
        }

        if (state == State.FINISHING || state == State.IDLE) {
            Log.i(TAG, "$debugInfo no need to stop")
            return
        }

        if (state == State.INITIALIZING) {
            Log.e(TAG, "$debugInfo Processing, wait for finish at $state")
        }
        state = State.FINISHING

        ensureWorkerExecute(::innerStop)
    }

    @WorkerThread
    protected fun innerStop() {
        workerHandler.removeCallbacks(renderTask)
        frames.clear()
        bitmapPool.clear()
        frameBuffer = null
        cachedCanvas.clear()
        bitmapReaderManager.closeReader()
        release()

        if (DEBUG) {
            Log.i(TAG, "$debugInfo release  and Set state to IDLE")
        }
        state = State.IDLE

        for (listener in renderListeners) {
            listener.onEnd()
        }
    }

    @WorkerThread
    protected abstract fun release()

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
    @Throws(IOException::class)
    protected fun initCanvasBounds() {
        val rect = read(bitmapReaderManager.getReader())
        fullRect = rect
        val capacityBytes = (rect.width() * rect.height() / (sampleSize * sampleSize) + 1) * 4
        frameBuffer = ByteBuffer.allocate(capacityBytes)
    }

    @Throws(IOException::class)
    protected abstract fun read(reader: R): Rect

    fun getMemorySize(): Int {
        val frameBufferSizeBytes = frameBuffer?.capacity() ?: 0
        return bitmapPool.getMemorySize() + frameBufferSizeBytes
    }

    fun setLoopLimit(limit: Int) {
        loopLimit = limit
    }

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

            // Schedule next frame
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

    /**
     * Rendering callbacks for decoders
     */
    interface RenderListener {
        /**
         * Playback starts
         */
        fun onStart()

        /**
         * Frame Playback
         */
        fun onRender(byteBuffer: ByteBuffer)

        /**
         * End of Playback
         */
        fun onEnd()
    }

    companion object {
        const val DEBUG = false
        val RECT_EMPTY = Rect()

        private const val TAG = "FrameDecoder"
    }
}