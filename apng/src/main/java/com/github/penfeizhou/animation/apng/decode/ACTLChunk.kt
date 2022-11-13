package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException

/**
 * @Description: https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG#.27acTL.27:_The_Animation_Control_Chunk
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class ACTLChunk(
    offset: Long,
    length: Int,
    fourCC: Int
) : Chunk(offset, length, fourCC) {
    var num_frames = 0
    var num_plays = 0

    @Throws(IOException::class)
    override fun innerParse(reader: FilterReader) {
        num_frames = reader.readInt()
        num_plays = reader.readInt()
    }

    companion object {
        val ID = fourCCToInt("acTL")
    }
}
