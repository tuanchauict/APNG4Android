package com.github.penfeizhou.animation.io

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-14
 */
class ByteBufferReader(private val byteBuffer: ByteBuffer) : Reader {
    init {
        byteBuffer.position(0)
    }

    override fun skip(total: Long): Long {
        byteBuffer.position((byteBuffer.position() + total).toInt())
        return total
    }

    override fun peek(): Byte = byteBuffer.get()

    override fun reset() {
        byteBuffer.position(0)
    }

    override fun position(): Int = byteBuffer.position()

    override fun read(buffer: ByteArray, start: Int, byteCount: Int): Int {
        byteBuffer.get(buffer, start, byteCount)
        return byteCount
    }

    override fun available(): Int {
        return byteBuffer.limit() - byteBuffer.position()
    }

    override fun close() = Unit

    override fun toInputStream(): InputStream = ByteArrayInputStream(byteBuffer.array())
}