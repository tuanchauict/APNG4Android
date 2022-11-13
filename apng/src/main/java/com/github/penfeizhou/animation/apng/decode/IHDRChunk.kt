package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException

/**
 * The IHDR chunk shall be the first chunk in the PNG datastream. It contains:
 *
 *
 * Width	4 bytes
 * Height	4 bytes
 * Bit depth	1 byte
 * Colour type	1 byte
 * Compression method	1 byte
 * Filter method	1 byte
 * Interlace method	1 byte
 *
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal class IHDRChunk(
    offset: Long,
    length: Int,
    fourCC: Int
) : Chunk(offset, length, fourCC) {
    /**
     * 图像宽度，以像素为单位
     */
    var width = 0

    /**
     * 图像高度，以像素为单位
     */
    var height = 0
    var data = ByteArray(5)

    @Throws(IOException::class)
    override fun innerParse(reader: FilterReader) {
        width = reader.readInt()
        height = reader.readInt()
        reader.read(data, 0, data.size)
    }

    companion object {
        val ID = fourCCToInt("IHDR")
    }
}
