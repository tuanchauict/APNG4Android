package com.github.penfeizhou.animation.decode

import android.os.Handler
import android.os.Looper
import com.github.penfeizhou.animation.executor.FrameDecoderExecutor

internal class FrameLooper(private val renderTask: Runnable) {

    private val workerHandler = Handler(
        FrameDecoderExecutor.getInstance()
            .getLooper(FrameDecoderExecutor.getInstance().generateTaskId())
    )

    fun schedule(delay: Long = 0) {
        if (delay > 0) {
            workerHandler.postDelayed(renderTask, delay)
        } else {
            workerHandler.post(renderTask)
        }
    }

    fun stop() {
        workerHandler.removeCallbacks(renderTask)
    }

    internal fun ensureWorkerExecute(block: () -> Unit) {
        if (Looper.myLooper() == workerHandler.looper) {
            println("#1: Thread ${Thread.currentThread().name}")
            block()
        } else {
            workerHandler.post {
                println("#2: Thread ${Thread.currentThread().name}")
                block()
            }
        }
    }
}