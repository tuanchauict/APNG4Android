package com.github.penfeizhou.animation.webp.io

import android.text.TextUtils
import com.github.penfeizhou.animation.io.Writer

/**
 * @Description: WebPWriter
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-12
 */
object WebPWriter {
    fun Writer.putUInt16(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
    }

    fun Writer.putUInt24(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
    }

    fun Writer.putUInt32(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
        putByte((value shr 24 and 0xff).toByte())
    }

    fun Writer.put1Based(i: Int) {
        putUInt24(i - 1)
    }

    fun Writer.putFourCC(fourCC: String) {
        if (TextUtils.isEmpty(fourCC) || fourCC.length != 4) {
            skip(4)
            return
        }
        putByte((fourCC[0].code and 0xff).toByte())
        putByte((fourCC[1].code and 0xff).toByte())
        putByte((fourCC[2].code and 0xff).toByte())
        putByte((fourCC[3].code and 0xff).toByte())
    }
}