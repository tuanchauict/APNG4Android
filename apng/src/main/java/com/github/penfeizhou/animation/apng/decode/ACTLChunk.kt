package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.io.FilterReader

/**
 * @Description: https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG#.27acTL.27:_The_Animation_Control_Chunk
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class ACTLChunk(
    offset: Long,
    length: Int,
    fourCC: Int,
    val num_frames: Int,
    val num_plays: Int,
    crc: Int
) : Chunk(offset, length, fourCC, crc) {

    internal class Parser(reader: FilterReader) : APNGParser.ChunkBodyParser {
        // Number of frames, 4 bytes
        private val num_frames: Int = reader.readInt()

        // Number of plays continuously, 4 bytes
        private val num_plays: Int = reader.readInt()

        override fun toChunk(prefix: APNGParser.ChunkPrefix, crc: Int): Chunk = ACTLChunk(
            offset = prefix.offset,
            length = prefix.length,
            fourCC = prefix.fourCC,
            num_frames = num_frames,
            num_plays = num_plays,
            crc = crc
        )
    }

    companion object {
        val ID = fourCCToInt("acTL")
    }
}
