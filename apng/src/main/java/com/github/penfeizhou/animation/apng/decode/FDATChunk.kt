package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader
import java.io.IOException

/**
 * @Description: https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG#.27fdAT.27:_The_Frame_Data_Chunk
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class FDATChunk(offset: Int, length: Int, fourCC: Int) : DATChunk(offset, length, fourCC) {
    var sequence_number = 0

    @Throws(IOException::class)
    override fun innerParse(reader: APNGReader) {
        sequence_number = reader.readInt()
    }

    companion object {
        val ID = fourCCToInt("fdAT")
    }
}