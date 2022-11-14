package com.github.penfeizhou.animation.apng.decode

/**
 * Length 4 bytes Specifies the length of the data field in the data block, which should not exceed (231-1) bytes
 * Chunk Type Code 4 bytes The Chunk Type Code consists of ASCII letters (A-Z and a-z).
 * Chunk Data Variable length Stores the data specified by the Chunk Type Code
 * CRC (Cyclic Redundancy Check) 4 bytes Stores the cyclic redundancy code used to check for errors
 * @Link https://www.w3.org/TR/PNG
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal sealed class Chunk(val offset: Long, val length: Int, val fourCC: Int, val crc: Int) {
    companion object {
        @JvmStatic
        fun fourCCToInt(fourCC: String): Int =
            if (fourCC.length == 4) {
                // cc[0].code & 0xFF << 0 | cc[1].code & 0xFF << 8 |
                // cc[2].code & 0xFF << 16 | cc[3].code & 0xFF << 24
                fourCC
                    .mapIndexed { index, char -> char.code and 0xff shl (index * 8) }
                    .reduce { acc, value -> acc or value }
            } else {
                -0x45210001
            }
    }
}

internal class FramePrefixChunk(
    offset: Long,
    length: Int,
    fourCC: Int,
    crc: Int
) : Chunk(offset, length, fourCC, crc) {
    object Parser : APNGParser.ChunkBodyParser {
        override fun toChunk(prefix: APNGParser.ChunkPrefix, crc: Int): Chunk = FramePrefixChunk(
            offset = prefix.offset,
            length = prefix.length,
            fourCC = prefix.fourCC,
            crc = crc
        )
    }
}

internal sealed interface FrameChunk

internal sealed interface DATChunk {
    val length: Int
}
