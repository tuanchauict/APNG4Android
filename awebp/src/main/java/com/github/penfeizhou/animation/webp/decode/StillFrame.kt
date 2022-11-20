package com.github.penfeizhou.animation.webp.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import java.io.IOException
import java.lang.IllegalArgumentException

/**
 * @Description: StillFrame
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
class StillFrame(
    private val reader: FilterReader,
    width: Int,
    height: Int
) : Frame(x = 0, y = 0, width = width, height = height, duration = 0) {

    override fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: Writer
    ): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inMutable = true
        options.inBitmap = reusedBitmap
        var bitmap: Bitmap? = null
        try {
            try {
                bitmap = BitmapFactory.decodeStream(reader.toInputStream(), null, options)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                // Problem decoding into existing bitmap when on Android 4.2.2 & 4.3
                val optionsFixed = BitmapFactory.Options()
                optionsFixed.inJustDecodeBounds = false
                optionsFixed.inSampleSize = sampleSize
                optionsFixed.inMutable = true
                bitmap = BitmapFactory.decodeStream(reader.toInputStream(), null, optionsFixed)
            }
            assert(bitmap != null)
            paint.xfermode = null
            canvas.drawBitmap(bitmap!!, 0f, 0f, paint)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bitmap
    }
}
