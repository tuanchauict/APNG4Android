package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.decode.Chunk.Companion.fourCCToInt

/**
 * @Description: 作用描述
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class IDATChunk : Chunk() {
    companion object {
        val ID = fourCCToInt("IDAT")
    }
}