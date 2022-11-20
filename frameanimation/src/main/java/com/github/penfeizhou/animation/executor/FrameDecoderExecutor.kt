package com.github.penfeizhou.animation.executor

import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

class FrameDecoderExecutor private constructor() {
    private val handlerThreads = mutableMapOf<Int, HandlerThread>()
    private val counter = AtomicInteger(0)

    var poolSize: Int = 4

    fun getLooper(taskId: Int): Looper {
        val idx = taskId % poolSize
        val handlerThread = handlerThreads.getOrPut(idx) { createAndStart(idx) }
        if (handlerThread.looper == null) {
            handlerThreads[idx] = createAndStart(idx)
        }
        return handlerThreads[idx]?.looper ?: Looper.getMainLooper()
    }

    private fun createAndStart(id: Int): HandlerThread {
        val handlerThread = HandlerThread("FrameDecoderExecutor-$id")
        handlerThread.start()
        return handlerThread
    }

    fun generateTaskId(): Int = counter.getAndIncrement()

    companion object {
        val instance = FrameDecoderExecutor()
    }
}
