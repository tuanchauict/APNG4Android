package com.github.penfeizhou.animation.gif.io

import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-16
 */
object GifReader {
    @Throws(IOException::class)
    fun FilterReader.readUInt16(): Int {
        val buf = ensureBytes()
        read(buf, 0, 2)
        return buf[0].toInt() and 0xff or (buf[1].toInt() and 0xff shl 8)
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
