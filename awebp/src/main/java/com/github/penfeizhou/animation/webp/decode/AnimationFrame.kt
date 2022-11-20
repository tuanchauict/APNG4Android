package com.github.penfeizhou.animation.webp.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.github.penfeizhou.animation.decode.KFrame
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.webp.io.WebPWriter.put1Based
import com.github.penfeizhou.animation.webp.io.WebPWriter.putFourCC
import com.github.penfeizhou.animation.webp.io.WebPWriter.putUInt24
import com.github.penfeizhou.animation.webp.io.WebPWriter.putUInt32
import java.io.IOException

class AnimationFrame(private val reader: FilterReader, anmfChunk: ANMFChunk) : KFrame(
    x = anmfChunk.frameX,
    y = anmfChunk.frameY,
    width = anmfChunk.frameWidth,
    height = anmfChunk.frameHeight,
    duration = if (anmfChunk.frameDuration == 0) 100 else anmfChunk.frameDuration
) {
    private val imagePayloadOffset: Int = anmfChunk.offset + BaseChunk.CHUNCK_HEADER_OFFSET + 16
    private val imagePayloadSize: Int = anmfChunk.payloadSize - 16 + (anmfChunk.payloadSize and 1)
    private val blendingMethod: Boolean = anmfChunk.blendingMethod()
    val disposalMethod: Boolean = anmfChunk.disposalMethod()
    private val useAlpha: Boolean = anmfChunk.alphChunk != null

    private val srcRect = Rect()
    private val dstRect = Rect()

    private fun encode(writer: Writer): Int {
        val vp8xPayloadSize = 10
        val size = 12 + (BaseChunk.CHUNCK_HEADER_OFFSET + vp8xPayloadSize) + imagePayloadSize
        writer.reset(size)
        // Webp Header
        writer.putFourCC("RIFF")
        writer.putUInt32(size)
        writer.putFourCC("WEBP")

        // VP8X
        writer.putUInt32(VP8XChunk.ID)
        writer.putUInt32(vp8xPayloadSize)
        writer.putByte((if (useAlpha) 0x10 else 0).toByte())
        writer.putUInt24(0)
        writer.put1Based(width)
        writer.put1Based(height)

        // ImageData
        try {
            reader.reset()
            reader.skip(imagePayloadOffset.toLong())
            reader.read(writer.toByteArray(), writer.position(), imagePayloadSize)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return size
    }

    override fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: Writer
    ): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inMutable = true
        options.inBitmap = reusedBitmap
        val length = encode(writer)
        val bytes = writer.toByteArray()
        var bitmap: Bitmap?
        try {
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, length, options)
        } catch (e: IllegalArgumentException) {
            // Problem decoding into existing bitmap when on Android 4.2.2 & 4.3
            val optionsFixed = BitmapFactory.Options()
            optionsFixed.inJustDecodeBounds = false
            optionsFixed.inSampleSize = sampleSize
            optionsFixed.inMutable = true
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, length, optionsFixed)
        }
        if (bitmap != null) {
            if (blendingMethod) {
                paint.xfermode = PORTERDUFF_XFERMODE_SRC
            } else {
                paint.xfermode = PORTERDUFF_XFERMODE_SRC_OVER
            }
            srcRect.left = 0
            srcRect.top = 0
            srcRect.right = bitmap.width
            srcRect.bottom = bitmap.height
            dstRect.left = (x.toFloat() * 2 / sampleSize).toInt()
            dstRect.top = (y.toFloat() * 2 / sampleSize).toInt()
            dstRect.right = (x.toFloat() * 2 / sampleSize + bitmap.width).toInt()
            dstRect.bottom = (y.toFloat() * 2 / sampleSize + bitmap.height).toInt()
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        } else {
            bitmap = reusedBitmap
        }
        return bitmap
    }

    companion object {
        private val PORTERDUFF_XFERMODE_SRC_OVER = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        private val PORTERDUFF_XFERMODE_SRC = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
}
