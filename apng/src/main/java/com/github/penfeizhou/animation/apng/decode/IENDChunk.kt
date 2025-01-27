package com.github.penfeizhou.animation.apng.decode

/**
 * @Description: 作用描述
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class IENDChunk(
    offset: Long,
    length: Int,
    fourCC: Int,
    crc: Int
) : Chunk(offset, length, fourCC, crc) {

    object Parser : APNGParser.ChunkBodyParser {
        override fun toChunk(prefix: APNGParser.ChunkPrefix, crc: Int): Chunk = IENDChunk(
            offset = prefix.offset,
            length = prefix.length,
            fourCC = prefix.fourCC,
            crc = crc
        )
    }

    companion object {
        val ID = fourCCToInt("IEND")
    }
}
