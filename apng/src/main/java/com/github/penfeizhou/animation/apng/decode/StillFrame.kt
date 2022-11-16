package com.github.penfeizhou.animation.apng.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import java.io.IOException
import java.lang.IllegalArgumentException

internal class StillFrame(private val reader: FilterReader, width: Int, height: Int) : Frame() {
    init {
        frameWidth = width
        frameHeight = height
    }

    override fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: Writer
    ): Bitmap? {
        val options = createBitmapFactoryOptions(sampleSize, reusedBitmap)
        var bitmap: Bitmap? = null
        try {
            reader.reset()
            bitmap = try {
                BitmapFactory.decodeStream(reader.toInputStream(), null, options)
            } catch (e: IllegalArgumentException) {
                // Problem decoding into existing bitmap when on Android 4.2.2 & 4.3
                val optionsFixed = createBitmapFactoryOptions(sampleSize, null)
                BitmapFactory.decodeStream(reader.toInputStream(), null, optionsFixed)
            }
            assert(bitmap != null)
            paint.xfermode = null
            canvas.drawBitmap(bitmap!!, 0f, 0f, paint)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bitmap
    }

    private fun createBitmapFactoryOptions(
        sampleSize: Int,
        reusedBitmap: Bitmap?
    ): BitmapFactory.Options = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sampleSize
        inMutable = true
        inBitmap = reusedBitmap
    }
}