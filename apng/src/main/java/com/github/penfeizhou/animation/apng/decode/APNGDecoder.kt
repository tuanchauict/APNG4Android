package com.github.penfeizhou.animation.apng.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.Size
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.decode.FrameSeqDecoder2
import com.github.penfeizhou.animation.decode.ImageInfo
import com.github.penfeizhou.animation.decode.RenderListener
import com.github.penfeizhou.animation.decode.area
import com.github.penfeizhou.animation.io.ByteBufferWriter
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * @param loader webp-like reader
 * @param renderListener Callbacks for rendering
 *
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
class APNGDecoder(
    loader: Loader,
    renderListener: RenderListener?
) : FrameSeqDecoder2(loader, renderListener) {
    private val paint = Paint().apply { isAntiAlias = true }

    private class SnapShot {
        var disposeOp: Byte = 0
        val dstRect = Rect()
        var byteBuffer: ByteBuffer? = null
    }

    private val snapShot = SnapShot()

    private val apngWriter: Writer by lazy { ByteBufferWriter(ByteOrder.BIG_ENDIAN) }

    override fun release() {
        snapShot.byteBuffer = null
    }

    @Throws(IOException::class)
    override fun read(reader: FilterReader): ImageInfo {
        val result = APNGParser.parse(reader)

        val isAnimated = result.actlChunk != null
        val loopCount = result.actlChunk?.num_plays ?: 1
        val viewport = Size(result.ihdrChunk.width, result.ihdrChunk.height)
        val frames = mutableListOf<Frame>()
        when {
            isAnimated ->
                result.frameDatas.mapIndexedTo(frames) { index, frameData ->
                    APNGFrame(
                        index,
                        reader,
                        frameData.fctlChunk,
                        result.ihdrChunk.data,
                        result.prefixChunks,
                        frameData.imageChunks
                    )
                }

            result.hasIDATChunk ->
                // If it is a non-APNG image, only PNG will be decoded
                frames += StillFrame(reader, viewport.width, viewport.height)
        }

        val bufferSizeBytes = (viewport.area / (sampleSize * sampleSize) + 1) * 4
        snapShot.byteBuffer = ByteBuffer.allocate(bufferSizeBytes)
        return ImageInfo(loopCount, viewport, frames)
    }

    override fun renderFrame(imageInfo: ImageInfo, frame: Frame, frameBuffer: ByteBuffer) {
        try {
            val bitmap = obtainBitmap(
                width = imageInfo.viewport.width / sampleSize,
                height = imageInfo.viewport.height / sampleSize
            ) ?: return
            val canvas = getCanvas(bitmap)

            if (frame is APNGFrame) {
                prepareApngBitmap(frame, bitmap, canvas, frameBuffer)
            }
            // Start actually drawing the content of the current frame
            val inBitmap = obtainBitmap(frame.frameWidth, frame.frameHeight)
            recycleBitmap(frame.draw(canvas, paint, sampleSize, inBitmap, apngWriter))
            recycleBitmap(inBitmap)
            frameBuffer.rewind()
            bitmap.copyPixelsToBuffer(frameBuffer)
            recycleBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // TODO: Not sure this is a suitable name
    private fun prepareApngBitmap(
        frame: APNGFrame,
        bitmap: Bitmap,
        canvas: Canvas,
        frameBuffer: ByteBuffer
    ) {
        // Restore the current frame from the cache
        frameBuffer.rewind()
        bitmap.copyPixelsFromBuffer(frameBuffer)
        // Process the settings in the snapshot before starting to draw
        if (frame.index == 0) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            canvas.save()
            canvas.clipRect(snapShot.dstRect)
            when (snapShot.disposeOp) {
                FCTLChunk.APNG_DISPOSE_OP_PREVIOUS -> {
                    snapShot.byteBuffer?.rewind()
                    bitmap.copyPixelsFromBuffer(snapShot.byteBuffer)
                }
                FCTLChunk.APNG_DISPOSE_OP_BACKGROUND ->
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                FCTLChunk.APNG_DISPOSE_OP_NON -> Unit
            }
            canvas.restore()
        }

        // Then pass it to the snapshot information according to the dispose setting
        if (frame.disposeOp == FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
            if (snapShot.disposeOp != FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
                snapShot.byteBuffer?.rewind()
                bitmap.copyPixelsToBuffer(snapShot.byteBuffer)
            }
        }
        snapShot.disposeOp = frame.disposeOp
        canvas.save()
        if (frame.blendOp == FCTLChunk.APNG_BLEND_OP_SOURCE) {
            canvas.clipRect(
                frame.frameX / sampleSize,
                frame.frameY / sampleSize,
                (frame.frameX + frame.frameWidth) / sampleSize,
                (frame.frameY + frame.frameHeight) / sampleSize
            )
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        snapShot.dstRect.set(
            frame.frameX / sampleSize,
            frame.frameY / sampleSize,
            (frame.frameX + frame.frameWidth) / sampleSize,
            (frame.frameY + frame.frameHeight) / sampleSize
        )
        canvas.restore()
    }
}
