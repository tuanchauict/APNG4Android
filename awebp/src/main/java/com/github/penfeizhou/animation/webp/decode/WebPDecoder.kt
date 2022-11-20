package com.github.penfeizhou.animation.webp.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Size
import com.github.penfeizhou.animation.decode.FrameSeqDecoder2
import com.github.penfeizhou.animation.decode.ImageInfo
import com.github.penfeizhou.animation.decode.KFrame
import com.github.penfeizhou.animation.io.ByteBufferWriter
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer

/**
 * @param loader         webp stream loader
 */
class WebPDecoder(loader: Loader) : FrameSeqDecoder2(loader) {
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
        val frames = mutableListOf<KFrame>()
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
        return ImageInfo(loopCount, Size(canvasWidth, canvasHeight), frames)
    }

    override fun renderFrame(imageInfo: ImageInfo, frame: KFrame, frameBuffer: ByteBuffer) {
        if (imageInfo.viewport.width <= 0 || imageInfo.viewport.height <= 0) {
            return
        }
        val bitmap = obtainBitmap(
            imageInfo.viewport.width / sampleSize,
            imageInfo.viewport.height / sampleSize
        ) ?: return
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
            val preFrame = imageInfo.frames[frameIndex - 1]
            // Dispose to background color. Fill the rectangle on the canvas covered by the current frame with background color specified in the ANIM chunk.
            if (preFrame is AnimationFrame &&
                preFrame.disposalMethod
            ) {
                val left = preFrame.x.toFloat() * 2 / sampleSize.toFloat()
                val top = preFrame.y.toFloat() * 2 / sampleSize.toFloat()
                val right =
                    (preFrame.x * 2 + preFrame.width).toFloat() / sampleSize.toFloat()
                val bottom =
                    (preFrame.y * 2 + preFrame.height).toFloat() / sampleSize.toFloat()
                canvas.drawRect(left, top, right, bottom, mTransparentFillPaint)
            }
        }
        var inBitmap: Bitmap? = null
        if (frame.width > 0 && frame.height > 0) {
            inBitmap = obtainBitmap(frame.width / sampleSize, frame.height / sampleSize)
        }
        if (inBitmap == null) {
            return
        }
        recycleBitmap(frame.draw(canvas, paint, sampleSize, inBitmap, writer))
        recycleBitmap(inBitmap)
        frameBuffer.rewind()
        bitmap.copyPixelsToBuffer(frameBuffer)
        recycleBitmap(bitmap)
    }
}
