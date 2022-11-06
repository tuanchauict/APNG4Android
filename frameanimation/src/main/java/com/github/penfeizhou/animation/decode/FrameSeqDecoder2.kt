package com.github.penfeizhou.animation.decode

import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import kotlin.math.min

abstract class FrameSeqDecoder2<R: Reader, W: Writer>(
    loader: Loader,
    renderListener: RenderListener?,
    readerFactory: (Reader) -> R
) : BaseFrameSeqDecoder<R, W>(loader, renderListener, readerFactory) {

    fun setDesiredSize(width: Int, height: Int): Boolean {
        val sample = getDesiredSample(width, height)
        if (sample == sampleSize) {
            return false
        }
        val isCurrentlyRunning = isRunning
        workerHandler.removeCallbacks(renderTask)

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
        var sample = 1
        while (sample * 2 <= radio) {
            sample *= 2
        }
        return sample
    }
}