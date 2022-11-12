package com.github.penfeizhou.animation.apng.io

import com.github.penfeizhou.animation.io.Writer

object APNGWriter {
    fun Writer.writeFourCC(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
        putByte((value shr 24 and 0xff).toByte())
    }

    fun Writer.writeInt(value: Int) {
        putByte((value shr 24 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value and 0xff).toByte())
    }
}