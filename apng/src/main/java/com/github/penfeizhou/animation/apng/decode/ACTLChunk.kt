package com.github.penfeizhou.animation.apng.decode

import kotlin.Throws
import com.github.penfeizhou.animation.apng.io.APNGReader
import java.io.IOException

/**
 * @Description: https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG#.27acTL.27:_The_Animation_Control_Chunk
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class ACTLChunk : Chunk() {
    var num_frames = 0
    var num_plays = 0

    @Throws(IOException::class)
    override fun innerParse(reader: APNGReader) {
        num_frames = reader.readInt()
        num_plays = reader.readInt()
    }

    companion object {
        val ID = fourCCToInt("acTL")
    }
}