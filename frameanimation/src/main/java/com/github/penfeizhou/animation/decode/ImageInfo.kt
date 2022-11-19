package com.github.penfeizhou.animation.decode

import android.util.Size

class ImageInfo(
    val loopCount: Int,
    val viewport: Size,
    val frames: List<Frame>
) {
    val area: Int = viewport.width * viewport.height

    companion object {
        val EMPTY = ImageInfo(loopCount = 0, Size(0, 0), emptyList())
    }
}

val Size.area: Int
    get() = width * height