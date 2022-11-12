package com.github.penfeizhou.animation.io

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * @Description: ByteBufferWriter
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-12
 */
class ByteBufferWriter(private val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN) : Writer {
    private var byteBuffer: ByteBuffer = ByteBuffer.allocate(0)

    init {
        reset(10 * 1024)
    }

    override fun reset(size: Int) {
        if (size > byteBuffer.capacity()) {
            byteBuffer = ByteBuffer.allocate(size)
            byteBuffer.order(byteOrder)
        }
        byteBuffer.clear()
    }

    override fun putByte(b: Byte) {
        byteBuffer.put(b)
    }

    override fun putBytes(b: ByteArray) {
        byteBuffer.put(b)
    }

    override fun position(): Int = byteBuffer.position()

    override fun skip(length: Int) {
        byteBuffer.position(length + position())
    }

    override fun toByteArray(): ByteArray = byteBuffer.array()

    override fun close() = Unit
}