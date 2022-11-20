package com.github.penfeizhou.animation.io

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.Throws
import kotlin.jvm.Synchronized

/**
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-11
 */
class StreamReader(inputStream: InputStream) : FilterInputStream(inputStream), Reader {
    private var position = 0

    init {
        try {
            inputStream.reset()
        } catch (e: IOException) {
            // e.printStackTrace();
        }
    }

    @Throws(IOException::class)
    override fun peek(): Byte {
        val ret = read().toByte()
        position++
        return ret
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, start: Int, byteCount: Int): Int {
        val ret = super.read(buffer, start, byteCount)
        position += ret.coerceAtLeast(0)
        return ret
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        position = 0
    }

    @Throws(IOException::class)
    override fun skip(total: Long): Long {
        val ret = super.skip(total)
        position += ret.toInt()
        return ret
    }

    override fun position(): Int = position

    override fun toInputStream(): InputStream = this
}
