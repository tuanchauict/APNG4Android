package com.github.penfeizhou.animation.apng.decode

/**
 * The IHDR chunk shall be the first chunk in the PNG datastream. It contains:
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
    fourCC: Int,
    val width: Int,
    val height: Int,
    val data: ByteArray
) : Chunk(offset, length, fourCC) {

    companion object {
        val ID = fourCCToInt("IHDR")
        val DUMMY = IHDRChunk(0, 0, ID, 0, 0, byteArrayOf())
    }
}
