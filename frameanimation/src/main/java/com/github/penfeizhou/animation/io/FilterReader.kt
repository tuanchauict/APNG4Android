package com.github.penfeizhou.animation.io

import java.io.IOException
import java.io.InputStream

/**
 * @Description: FilterReader
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-23
 */
open class FilterReader(
    @JvmField
    protected var reader: Reader
) : Reader {
    @Throws(IOException::class)
    override fun skip(total: Long): Long = reader.skip(total)

    @Throws(IOException::class)
    override fun peek(): Byte = reader.peek()

    @Throws(IOException::class)
    override fun reset() = reader.reset()

    override fun position(): Int = reader.position()

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, start: Int, byteCount: Int): Int =
        reader.read(buffer, start, byteCount)

    @Throws(IOException::class)
    override fun available(): Int = reader.available()

    @Throws(IOException::class)
    override fun close() = reader.close()

    @Throws(IOException::class)
    override fun toInputStream(): InputStream {
        reset()
        return reader.toInputStream()
    }
}