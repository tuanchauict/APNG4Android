package com.github.penfeizhou.animation.io

import java.io.IOException
import java.lang.UnsupportedOperationException
import java.nio.IntBuffer
import kotlin.Throws

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-12
 */
interface Writer {
    fun reset(size: Int)
    fun putByte(b: Byte)
    fun putBytes(b: ByteArray)
    fun position(): Int
    fun skip(length: Int)
    fun toByteArray(): ByteArray

    @Throws(IOException::class)
    fun close()

    fun asIntArray(): IntArray {
        throw UnsupportedOperationException()
    }

    fun asIntBuffer(): IntBuffer {
        throw UnsupportedOperationException()
    }
}
