package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.IntRange
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import kotlin.math.min

abstract class FrameSeqDecoder2(
    loader: Loader,
    renderListener: RenderListener?
) : BaseFrameSeqDecoder(loader, renderListener) {

    fun setDesiredSize(width: Int, height: Int): Boolean {
        val sample = getDesiredSample(width, height)
        if (sample == sampleSize) {
            return false
        }
        val isCurrentlyRunning = isRunning

        ensureWorkerExecute {
            innerStop()
            sampleSize = sample
            try {
                initCanvasBounds()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (isCurrentlyRunning) {
                innerStart()
            }
        }

        return true
    }

    protected open fun getDesiredSample(desiredWidth: Int, desiredHeight: Int): Int {
        if (desiredWidth == 0 || desiredHeight == 0) {
            return 1
        }
        val bound = getBounds()
        val radio = min(
            bound.width() / desiredWidth,
            bound.height() / desiredHeight
        )
        return if (radio > 0) radio.takeHighestOneBit() else 1
    }

    @Throws(IOException::class)
    fun getFrameBitmap(@IntRange(from = 0) index: Int): Bitmap? {
        if (state != State.IDLE) {
            Log.e(TAG, "Stop first")
            return null
        }

        state = State.RUNNING
        paused.set(false)

        if (frameCount == 0) {
            initCanvasBounds()
        }

        frameIndex = -1
        while (frameIndex < index) {
            if (canStep()) {
                step()
            } else {
                break
            }
        }
        val nonNullFrameBuffer = currentFrameBuffer ?: return null
        val bounds = getBounds()
        val bitmap = Bitmap.createBitmap(
            bounds.width() / sampleSize,
            bounds.height() / sampleSize,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(nonNullFrameBuffer)
        innerStop()
        return bitmap
    }
}
