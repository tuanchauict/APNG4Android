package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.io.FilterReader

/**
 * The IHDR chunk shall be the first chunk in the PNG datastream. It contains:
 *
 * Width	4 bytes
 * Height	4 bytes
 * Bit depth	1 byte
 * Colour type	1 byte
 * Compression method	1 byte
 * Filter method	1 byte
 * Interlace method	1 byte
 *
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class IHDRChunk(
    offset: Long,
    length: Int,
    fourCC: Int,
    val width: Int,
    val height: Int,
    val data: ByteArray,
    crc: Int
) : Chunk(offset, length, fourCC, crc) {

    class Parser(reader: FilterReader) : APNGParser.ChunkBodyParser {
        private val width = reader.readInt()
        private val height = reader.readInt()
        private val data = ByteArray(5).also { reader.read(it, 0, it.size) }

        override fun toChunk(prefix: APNGParser.ChunkPrefix, crc: Int): Chunk = IHDRChunk(
            offset = prefix.offset,
            length = prefix.length,
            fourCC = prefix.fourCC,
            width = width,
            height = height,
            data = data,
            crc = crc
        )
    }

    companion object {
        val ID = fourCCToInt("IHDR")
        val DUMMY = IHDRChunk(0, 0, ID, 0, 0, byteArrayOf(), 0)
    }
}
