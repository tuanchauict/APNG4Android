package com.github.penfeizhou.animation.decode

import android.graphics.Bitmap

internal class BitmapPool {
    private val pool: MutableSet<Bitmap> = mutableSetOf()

    fun obtain(width: Int, height: Int): Bitmap? = synchronized(this) {
        val reuseSize = width * height
        val bitmap = pool.firstOrNull { it.allocationByteCount >= reuseSize }
        if (bitmap != null) {
            pool.remove(bitmap)
            bitmap.eraseColor(0)
            bitmap.reconfigureBitmapIfNeed(width, height)

            return@synchronized bitmap
        }

        if (width > 0 && height > 0) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            null
        }
    }

    private fun Bitmap.reconfigureBitmapIfNeed(width: Int, height: Int) {
        if (this.width != width || this.height != height) {
            if (width > 0 && height > 0) {
                reconfigure(width, height, Bitmap.Config.ARGB_8888)
            }
        }
    }

    fun recycle(bitmap: Bitmap?) {
        synchronized(this) {
            if (bitmap != null) {
                pool.add(bitmap)
            }
        }
    }

    fun clear() {
        synchronized(this) {
            for (bitmap in pool) {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    fun getMemorySize(): Int = synchronized(this) {
        pool.sumOf { if (it.isRecycled) 0 else it.allocationByteCount }
    }
}
