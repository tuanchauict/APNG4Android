package com.github.penfeizhou.animation.apng.decode

/**
 * @Description: 作用描述
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class IDATChunk(
    offset: Long,
    length: Int,
    fourCC: Int,
    crc: Int
) : Chunk(offset, length, fourCC, crc), FrameChunk, DATChunk {
    companion object {
        val ID = fourCCToInt("IDAT")
    }
}
