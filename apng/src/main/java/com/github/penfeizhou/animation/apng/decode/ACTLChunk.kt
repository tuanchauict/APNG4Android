package com.github.penfeizhou.animation.apng.decode

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
    val num_plays: Int
) : Chunk(offset, length, fourCC) {
    companion object {
        val ID = fourCCToInt("acTL")
    }
}
