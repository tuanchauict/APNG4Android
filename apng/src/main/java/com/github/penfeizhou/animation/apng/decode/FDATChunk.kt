package com.github.penfeizhou.animation.apng.decode

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

    companion object {
        val ID = fourCCToInt("fdAT")
    }
}
