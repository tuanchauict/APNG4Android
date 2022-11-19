package com.github.penfeizhou.animation.decode

import android.util.Size

class ImageInfo(
    val loopCount: Int,
    val viewport: Size
) {
    val area: Int = viewport.width * viewport.height

    constructor(loopCount: Int, viewportWidthPx: Int, viewportHeightPx: Int) : this(
        loopCount,
        Size(viewportWidthPx, viewportHeightPx)
    )

    companion object {
        val EMPTY = ImageInfo(loopCount = 0, viewportWidthPx = 0, viewportHeightPx = 0)
    }
}