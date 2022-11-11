package com.github.penfeizhou.animation.webp.io

import android.text.TextUtils
import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException

/**
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-11
 */
object WebPReader {
    /**
     * @return uint16 A 16-bit, little-endian, unsigned integer.
     */
    @Throws(IOException::class)
    fun FilterReader.readUInt16(): Int {
        val buf = ensureBytes()
        read(buf, 0, 2)
        return buf[0].toInt() and 0xff or (buf[1].toInt() and 0xff shl 8)
    }

    /**
     * @return uint24 A 24-bit, little-endian, unsigned integer.
     */
    @Throws(IOException::class)
    fun FilterReader.readUInt24(): Int {
        val buf = ensureBytes()
        read(buf, 0, 3)
        return buf[0].toInt() and 0xff or (buf[1].toInt() and 0xff shl 8) or (buf[2].toInt() and 0xff shl 16)
    }

    /**
     * @return uint32 A 32-bit, little-endian, unsigned integer.
     */
    @Throws(IOException::class)
    fun FilterReader.readUInt32(): Int {
        val buf = ensureBytes()
        read(buf, 0, 4)
        return buf[0].toInt() and 0xff or (buf[1].toInt() and 0xff shl 8) or (buf[2].toInt() and 0xff shl 16) or (buf[3].toInt() and 0xff shl 24)
    }

    /**
     * @return FourCC A FourCC (four-character code) is a uint32 created by concatenating four ASCII characters in little-endian order.
     */
    @Throws(IOException::class)
    fun FilterReader.readFourCC(): Int {
        val buf = ensureBytes()
        read(buf, 0, 4)
        return buf[0].toInt() and 0xff or (buf[1].toInt() and 0xff shl 8) or (buf[2].toInt() and 0xff shl 16) or (buf[3].toInt() and 0xff shl 24)
    }

    /**
     * @return 1-based An unsigned integer field storing values offset by -1. e.g., Such a field would store value 25 as 24.
     */
    @Throws(IOException::class)
    fun FilterReader.read1Based(): Int {
        return readUInt24() + 1
    }

    /**
     * @return read FourCC and match chars
     */
    @Throws(IOException::class)
    fun FilterReader.matchFourCC(chars: String): Boolean {
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
