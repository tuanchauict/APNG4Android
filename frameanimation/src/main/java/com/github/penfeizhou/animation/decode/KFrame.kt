package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.github.penfeizhou.animation.io.Writer

abstract class KFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val duration: Int
) {
    protected val srcRect = Rect()
    protected val dstRect = Rect()

    abstract fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: Writer
    ): Bitmap?
}
