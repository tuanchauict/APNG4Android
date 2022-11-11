package com.github.penfeizhou.animation.webp.io

import android.text.TextUtils
import com.github.penfeizhou.animation.io.ByteBufferWriter

/**
 * @Description: WebPWriter
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-12
 */
class WebPWriter : ByteBufferWriter() {
    fun putUInt16(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
    }

    fun putUInt24(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
    }

    fun putUInt32(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
        putByte((value shr 24 and 0xff).toByte())
    }

    fun put1Based(i: Int) {
        putUInt24(i - 1)
    }

    fun putFourCC(fourCC: String) {
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