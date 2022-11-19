package com.github.penfeizhou.animation.webp.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.decode.FrameSeqDecoder2
import com.github.penfeizhou.animation.decode.ImageInfo
import com.github.penfeizhou.animation.io.ByteBufferWriter
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer

/**
 * @param loader         webp stream loader
 * @param renderListener callback for rendering
 */
class WebPDecoder(
    loader: Loader,
    renderListener: RenderListener?
) : FrameSeqDecoder2(loader, renderListener) {
    private val mTransparentFillPaint: Paint = Paint().apply {
        color = Color.TRANSPARENT
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    private val paint: Paint by lazy {
        val value = Paint()
        value.isAntiAlias = true
        value
    }
    private var canvasWidth = 0
    private var canvasHeight = 0
    private var alpha = false
    private var backgroundColor = 0
    private val writer: Writer by lazy { ByteBufferWriter() }

    override fun release() {}

    @Throws(IOException::class)
    override fun read(reader: FilterReader): ImageInfo {
        val chunks = WebPParser.parse(reader)
        var anim = false
        var vp8x = false
        var loopCount = 0
        for (chunk in chunks) {
            when (chunk) {
                is VP8XChunk -> {
                    canvasWidth = chunk.canvasWidth
                    canvasHeight = chunk.canvasHeight
                    alpha = chunk.alpha()
                    vp8x = true
                }
                is ANIMChunk -> {
                    anim = true
                    backgroundColor = chunk.backgroundColor
                    loopCount = chunk.loopCount
                }
                is ANMFChunk -> frames.add(AnimationFrame(reader, chunk))
            }
        }
        if (!anim) {
            // 静态图
            if (!vp8x) {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(reader.toInputStream(), null, options)
                canvasWidth = options.outWidth
                canvasHeight = options.outHeight
            }
            frames.add(StillFrame(reader, canvasWidth, canvasHeight))
            loopCount = 1
        }
        if (!alpha) {
            mTransparentFillPaint.color = backgroundColor
        }
        return ImageInfo(loopCount, canvasWidth, canvasHeight)
    }

    override fun renderFrame(frame: Frame, frameBuffer: ByteBuffer) {
        if (viewport == null) {
            return
        }
        if (viewport!!.width() <= 0 || viewport!!.height() <= 0) {
            return
        }
        val bitmap = obtainBitmap(viewport!!.width() / sampleSize, viewport!!.height() / sampleSize)
            ?: return
        val canvas = getCanvas(bitmap)
        // 从缓存中恢复当前帧
        frameBuffer.rewind()
        bitmap.copyPixelsFromBuffer(frameBuffer)
        if (frameIndex == 0) {
            if (alpha) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC)
            } else {
                canvas.drawColor(backgroundColor, PorterDuff.Mode.SRC)
            }
        } else {
            val preFrame = frames[frameIndex - 1]
            // Dispose to background color. Fill the rectangle on the canvas covered by the current frame with background color specified in the ANIM chunk.
            if (preFrame is AnimationFrame &&
                preFrame.disposalMethod
            ) {
                val left = preFrame.frameX.toFloat() * 2 / sampleSize.toFloat()
                val top = preFrame.frameY.toFloat() * 2 / sampleSize.toFloat()
                val right =
                    (preFrame.frameX * 2 + preFrame.frameWidth).toFloat() / sampleSize.toFloat()
                val bottom =
                    (preFrame.frameY * 2 + preFrame.frameHeight).toFloat() / sampleSize.toFloat()
                canvas.drawRect(left, top, right, bottom, mTransparentFillPaint)
            }
        }
        var inBitmap: Bitmap? = null
        if (frame.frameWidth > 0 && frame.frameHeight > 0) {
            inBitmap = obtainBitmap(frame.frameWidth / sampleSize, frame.frameHeight / sampleSize)
        }
        recycleBitmap(frame.draw(canvas, paint, sampleSize, inBitmap, writer))
        recycleBitmap(inBitmap)
        frameBuffer.rewind()
        bitmap.copyPixelsToBuffer(frameBuffer)
        recycleBitmap(bitmap)
    }

    companion object {
        private val TAG = WebPDecoder::class.java.simpleName
    }
}
