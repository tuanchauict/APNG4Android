package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.io.FilterReader

/**
 * @Description: https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG#.27fdAT.27:_The_Frame_Data_Chunk
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class FDATChunk(
    offset: Long,
    length: Int,
    fourCC: Int,
    val sequence_number: Int,
    crc: Int
) : Chunk(offset, length, fourCC, crc), FrameChunk, DATChunk {

    class Parser(reader: FilterReader) : APNGParser.ChunkBodyParser {
        private val sequence_number = reader.readInt()
        override fun toChunk(prefix: APNGParser.ChunkPrefix, crc: Int): Chunk = FDATChunk(
            offset = prefix.offset,
            length = prefix.length,
            fourCC = prefix.fourCC,
            sequence_number = sequence_number,
            crc = crc
        )
    }

    companion object {
        val ID = fourCCToInt("fdAT")
    }
}
