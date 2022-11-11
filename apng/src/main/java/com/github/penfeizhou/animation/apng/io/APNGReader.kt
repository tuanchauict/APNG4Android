package com.github.penfeizhou.animation.apng.io

import android.text.TextUtils
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Reader
import java.io.IOException

/**
 * @Description: APNGReader
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
class APNGReader(reader: Reader) : FilterReader(reader) {
    @Throws(IOException::class)
    fun readInt(): Int {
        val buf = ensureBytes()
        read(buf, 0, 4)
        return buf[3].toInt() and 0xFF or (
                buf[2].toInt() and 0xFF shl 8) or (
                buf[1].toInt() and 0xFF shl 16) or (
                buf[0].toInt() and 0xFF shl 24)
    }

    @Throws(IOException::class)
    fun readShort(): Short {
        val buf = ensureBytes()
        read(buf, 0, 2)
        return (buf[1].toInt() and 0xFF or (
                buf[0].toInt() and 0xFF shl 8)).toShort()
    }

    /**
     * @return read FourCC and match chars
     */
    @Throws(IOException::class)
    fun matchFourCC(chars: String): Boolean {
        if (TextUtils.isEmpty(chars) || chars.length != 4) {
            return false
        }
        val fourCC = readFourCC()
        for (i in 0..3) {
            if (fourCC shr i * 8 and 0xff != chars[i].code) {
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    fun readFourCC(): Int {
        val buf = ensureBytes()
        read(buf, 0, 4)
        return buf[0].toInt() and 0xff or (buf[1].toInt() and 0xff shl 8) or (buf[2].toInt() and 0xff shl 16) or (buf[3].toInt() and 0xff shl 24)
    }

    companion object {
        private val THREAD_LOCAL_BYTE_BUFFERS = ThreadLocal<ByteArray>()

        private fun ensureBytes(): ByteArray {
            var bytes = THREAD_LOCAL_BYTE_BUFFERS.get()
            if (bytes == null) {
                bytes = ByteArray(4)
                THREAD_LOCAL_BYTE_BUFFERS.set(bytes)
            }
            return bytes
        }
    }
}