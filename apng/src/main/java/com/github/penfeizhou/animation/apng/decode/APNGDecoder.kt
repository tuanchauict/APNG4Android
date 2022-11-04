package com.github.penfeizhou.animation.apng.decode

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import com.github.penfeizhou.animation.apng.io.APNGReader
import com.github.penfeizhou.animation.apng.io.APNGWriter
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.decode.FrameSeqDecoder
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer

/**
 * @param loader webp-like reader
 * @param renderListener Callbacks for rendering
 *
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
class APNGDecoder(
    loader: Loader?,
    renderListener: RenderListener?
) : FrameSeqDecoder<APNGReader, APNGWriter>(loader, renderListener) {
    private var apngWriter: APNGWriter? = null
    private var mLoopCount = 0
    private val paint = Paint().apply { isAntiAlias = true }

    private class SnapShot {
        var disposeOp: Byte = 0
        var dstRect = Rect()
        var byteBuffer: ByteBuffer? = null
    }

    private val snapShot = SnapShot()

    override fun getWriter(): APNGWriter {
        val writer = apngWriter ?: APNGWriter()
        apngWriter = writer
        return writer
    }

    override fun getReader(reader: Reader): APNGReader {
        return APNGReader(reader)
    }

    override fun getLoopCount(): Int {
        return mLoopCount
    }

    override fun release() {
        snapShot.byteBuffer = null
        apngWriter = null
    }

    @Throws(IOException::class)
    override fun read(reader: APNGReader): Rect {
        val chunks = APNGParser.parse(reader)
        val otherChunks = mutableListOf<Chunk>()
        var actl = false
        var lastFrame: APNGFrame? = null
        var ihdrData: ByteArray? = ByteArray(0)
        var canvasWidth = 0
        var canvasHeight = 0
        for (chunk in chunks) {
            when (chunk) {
                is ACTLChunk -> {
                    mLoopCount = chunk.num_plays
                    actl = true
                }
                is FCTLChunk -> {
                    val frame = APNGFrame(reader, chunk).also {
                        it.prefixChunks = otherChunks
                        it.ihdrData = ihdrData
                    }
                    frames.add(frame)
                    lastFrame = frame
                }
                is FDATChunk ->
                    lastFrame?.imageChunks?.add(chunk)
                is IDATChunk -> {
                    if (!actl) {
                        // If it is a non-APNG image, only PNG will be decoded
                        val frame = StillFrame(reader).apply {
                            frameWidth = canvasWidth
                            frameHeight = canvasHeight
                        }
                        frames.add(frame)
                        mLoopCount = 1
                        break
                    }
                    lastFrame?.imageChunks?.add(chunk)
                }
                is IHDRChunk -> {
                    canvasWidth = chunk.width
                    canvasHeight = chunk.height
                    ihdrData = chunk.data
                }
                is IENDChunk -> Unit
                is Chunk -> otherChunks.add(chunk)
            }
        }
        val bufferSizeBytes = (canvasWidth * canvasHeight / (sampleSize * sampleSize) + 1) * 4
        frameBuffer = ByteBuffer.allocate(bufferSizeBytes)
        snapShot.byteBuffer = ByteBuffer.allocate(bufferSizeBytes)
        return Rect(0, 0, canvasWidth, canvasHeight)
    }

    override fun renderFrame(frame: Frame<APNGReader, APNGWriter>) {
        if (fullRect == null) {
            return
        }
        try {
            val bitmap = obtainBitmap(fullRect.width() / sampleSize, fullRect.height() / sampleSize)
            var canvas = cachedCanvas[bitmap]
            if (canvas == null) {
                canvas = Canvas(bitmap)
                cachedCanvas[bitmap] = canvas
            }
            if (frame is APNGFrame) {
                // Restore the current frame from the cache
                frameBuffer.rewind()
                bitmap.copyPixelsFromBuffer(frameBuffer)
                // Process the settings in the snapshot before starting to draw
                if (frameIndex == 0) {
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
                if (frame.dispose_op == FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
                    if (snapShot.disposeOp != FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
                        snapShot.byteBuffer?.rewind()
                        bitmap.copyPixelsToBuffer(snapShot.byteBuffer)
                    }
                }
                snapShot.disposeOp = frame.dispose_op
                canvas.save()
                if (frame.blend_op == FCTLChunk.APNG_BLEND_OP_SOURCE) {
                    canvas.clipRect(
                        frame.frameX / sampleSize,
                        frame.frameY / sampleSize,
                        (frame.frameX + frame.frameWidth) / sampleSize,
                        (frame.frameY + frame.frameHeight) / sampleSize
                    )
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                }
                snapShot.dstRect[frame.frameX / sampleSize, frame.frameY / sampleSize, (frame.frameX + frame.frameWidth) / sampleSize] =
                    (frame.frameY + frame.frameHeight) / sampleSize
                canvas.restore()
            }
            // Start actually drawing the content of the current frame
            val inBitmap = obtainBitmap(frame.frameWidth, frame.frameHeight)
            recycleBitmap(frame.draw(canvas, paint, sampleSize, inBitmap, writer))
            recycleBitmap(inBitmap)
            frameBuffer.rewind()
            bitmap.copyPixelsToBuffer(frameBuffer)
            recycleBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}