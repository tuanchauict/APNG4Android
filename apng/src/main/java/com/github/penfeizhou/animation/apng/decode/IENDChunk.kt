package com.github.penfeizhou.animation.apng.decode

/**
 * @Description: 作用描述
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class IENDChunk(
    offset: Long,
    length: Int,
    fourCC: Int
) : Chunk(offset, length, fourCC) {
    companion object {
        val ID = fourCCToInt("IEND")
    }
}
