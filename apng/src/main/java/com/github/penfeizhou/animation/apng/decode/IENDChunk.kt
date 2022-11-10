package com.github.penfeizhou.animation.apng.decode

/**
 * @Description: 作用描述
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class IENDChunk : Chunk() {
    companion object {
        val ID = fourCCToInt("IEND")
    }
}