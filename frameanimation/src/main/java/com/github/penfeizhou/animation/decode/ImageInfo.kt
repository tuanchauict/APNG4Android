package com.github.penfeizhou.animation.decode

import android.graphics.Rect
import android.util.Size

class ImageInfo(
    val loopCount: Int,
    val viewport: Rect
) {
    val size: Size = Size(viewport.width(), viewport.height())
    val viewportWidthPx: Int = viewport.width()
    val viewportHeightPx: Int = viewport.height()
    val area: Int = viewport.width() * viewport.height()

    constructor(loopCount: Int, viewportWidth: Int, viewportHeightPx: Int) : this(
        loopCount,
        Rect(0, 0, viewportWidth, viewportHeightPx)
    )

    companion object {
        val EMPTY = ImageInfo(loopCount = 0, viewportWidth = 0, viewportHeightPx = 0)
    }
}