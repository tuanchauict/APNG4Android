package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.util.Size
import androidx.annotation.WorkerThread
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

abstract class BaseFrameSeqDecoder(
    protected val loader: Loader,
    private val currentTimeProvider: () -> Long = System::currentTimeMillis
) {
    private var frameBuffer: ByteBuffer? = null

    // TODO: Remove this
    val currentFrameBuffer: ByteBuffer?
        get() = frameBuffer

    protected var frameIndex = -1

    internal val paused = AtomicBoolean(true)

    private val renderListeners: MutableSet<RenderListener> = mutableSetOf()

    internal val frameLooper = FrameLooper(::onFrame)

    private val bitmapPool = BitmapPool()
    private val bitmapReaderManager = BitmapReaderManager(loader)

    private val cachedCanvas: MutableMap<Bitmap, Canvas> = WeakHashMap()

    private var playCount: Int = 0

    private var loopLimit: Int? = null

    private val numPlays: Int
        get() = loopLimit ?: imageInfo?.loopCount ?: 0

    /**
     * If played all the needed
     */
    private var finished: Boolean = false

    @Volatile
    internal var state = State.IDLE
        set(value) {
            field = value
            if (DEBUG) {
                Log.i(TAG, "$debugInfo Update state to $value")
            }
        }

    @Volatile
    private var imageInfo: ImageInfo? = null

    val frameCount: Int
        get() = imageInfo?.frames?.size ?: 0

    var sampleSize = 1
        internal set

    val isRunning: Boolean
        get() = state == State.RUNNING || state == State.INITIALIZING

    private val debugInfo: String
        get() = if (DEBUG) {
            "Thread is ${Thread.currentThread()}, decoder is $this, state is $state"
        } else {
            ""
        }

    fun getViewport(): Size {
        if (imageInfo == null) {
            if (state == State.FINISHING) {
                Log.e(TAG, "$debugInfo in finishing. Do not interrupt")
            }
            val thread = Thread.currentThread()
            frameLooper.ensureWorkerExecute {
                try {
                    if (imageInfo == null) {
                        initCanvasBounds()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    imageInfo = ImageInfo.EMPTY
                } finally {
                    LockSupport.unpark(thread)
                }
            }
            LockSupport.park(thread)
        }
        val imageInfo = imageInfo ?: ImageInfo.EMPTY
        return imageInfo.viewport
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

    protected fun getCanvas(bitmap: Bitmap): Canvas =
        cachedCanvas.getOrPut(bitmap) { Canvas(bitmap) }

    private fun onFrame() {
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
        frameLooper.schedule(delay - cost)

        val frameBuffer = frameBuffer ?: return
        for (listener in renderListeners) {
            listener.onRender(frameBuffer)
        }
    }

    internal fun canStep(): Boolean {
        if (!isRunning || frameCount == 0) {
            return false
        }
        val numPlays = numPlays
        if (numPlays <= 0) {
            return true
        }

        if (playCount < numPlays - 1) {
            return true
        } else if (playCount == numPlays - 1 && frameIndex < frameCount - 1) {
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
        if (frameIndex >= frameCount) {
            frameIndex = 0
            playCount += 1
        }

        val frame = getFrame(frameIndex) ?: return 0
        val frameBuffer = frameBuffer ?: return 0
        val imageInfo = imageInfo ?: return 0
        renderFrame(imageInfo, frame, frameBuffer)
        return frame.frameDuration.toLong()
    }

    fun start() {
        if (imageInfo == ImageInfo.EMPTY) {
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
        frameLooper.ensureWorkerExecute(::innerStart)
    }

    @WorkerThread
    internal fun innerStart() {
        paused.set(false)
        val startTimeMillis = System.currentTimeMillis()

        if (frameCount == 0) {
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
                |cost = ${System.currentTimeMillis() - startTimeMillis}
            """.trimMargin()
        )
        state = State.RUNNING

        if (numPlays == 0 || !finished) {
            frameIndex = -1
            frameLooper.schedule()

            for (listener in renderListeners) {
                listener.onStart()
            }
        } else {
            Log.i(TAG, "$debugInfo No need to start")
        }
    }

    fun stopIfNeeded() = frameLooper.ensureWorkerExecute {
        if (renderListeners.isEmpty()) {
            stop()
        }
    }

    fun stop() {
        if (imageInfo == ImageInfo.EMPTY) {
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

        frameLooper.ensureWorkerExecute(::innerStop)
    }

    @WorkerThread
    internal fun innerStop() {
        frameLooper.stop()
        imageInfo = null
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
        paused.set(false)
        frameLooper.stop()
        frameLooper.schedule()
    }

    fun pause() {
        frameLooper.stop()
        paused.set(true)
    }

    fun reset() = frameLooper.ensureWorkerExecute {
        playCount = 0
        frameIndex = -1
        finished = false
    }

    fun isPaused(): Boolean = paused.get()

    fun addRenderListener(listener: RenderListener) =
        frameLooper.ensureWorkerExecute { renderListeners.add(listener) }

    fun removeRenderListener(listener: RenderListener) =
        frameLooper.ensureWorkerExecute { renderListeners.remove(listener) }

    @WorkerThread
    protected abstract fun renderFrame(imageInfo: ImageInfo, frame: KFrame, frameBuffer: ByteBuffer)

    fun getFrame(index: Int): KFrame? = imageInfo?.frames?.getOrNull(index)

    // TODO: Rename this method. Init canvas bounds is not appropriate anymore.
    @WorkerThread
    @Throws(IOException::class)
    internal fun initCanvasBounds() {
        val imageInfo = read(bitmapReaderManager.getReader())
        this.imageInfo = imageInfo
        val capacityBytes = (imageInfo.area / (sampleSize * sampleSize) + 1) * 4
        frameBuffer = ByteBuffer.allocate(capacityBytes)
    }

    @Throws(IOException::class)
    protected abstract fun read(reader: FilterReader): ImageInfo

    fun getMemorySize(): Int {
        val frameBufferSizeBytes = frameBuffer?.capacity() ?: 0
        return bitmapPool.getMemorySize() + frameBufferSizeBytes
    }

    fun setLoopLimit(limit: Int) {
        loopLimit = limit
    }

    internal enum class State {
        IDLE, RUNNING, INITIALIZING, FINISHING
    }

    companion object {
        const val DEBUG = false

        const val TAG = "FrameDecoder"
    }
}
