package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.github.penfeizhou.animation.io.Writer

abstract class KFrame(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    duration: Int
) : Frame() {

    init {
        this.frameX = x
        this.frameY = y
        this.frameWidth = width
        this.frameHeight = height
        this.frameDuration = duration
    }

    abstract override fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: Writer
    ): Bitmap?
}