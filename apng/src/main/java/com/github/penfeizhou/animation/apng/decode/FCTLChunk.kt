package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.apng.io.APNGReader.readShort
import com.github.penfeizhou.animation.io.FilterReader

/**
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 * @see {link=https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG..27fcTL.27:_The_Frame_Control_Chunk}
 *
 * x_offset >= 0
 * y_offset >= 0
 * width > 0
 * height > 0
 * x_offset + width <= 'IHDR' width
 * y_offset + height <= 'IHDR' height
 */
internal class FCTLChunk(
    offset: Long,
    length: Int,
    fourCC: Int,
    val sequence_number: Int,
    /**
     * Width of the following frame.
     */
    val width: Int,

    /**
     * Height of the following frame.
     */
    val height: Int,

    /**
     * X position at which to render the following frame.
     */
    val x_offset: Int,

    /**
     * Y position at which to render the following frame.
     */
    val y_offset: Int,
    /**
     * The delay_num and delay_den parameters together specify a fraction indicating the time to
     * display the current frame, in seconds. If the denominator is 0, it is to be treated as if it
     * were 100 (that is, delay_num then specifies 1/100ths of a second).
     * If the the value of the numerator is 0 the decoder should render the next frame as quickly as
     * possible, though viewers may impose a reasonable lower bound.
     *
     *
     * Frame timings should be independent of the time required for decoding and display of each frame,
     * so that animations will run at the same rate regardless of the performance of the decoder implementation.
     */
    /**
     * Frame delay fraction numerator.
     */
    val delay_num: Short,

    /**
     * Frame delay fraction denominator.
     */
    val delay_den: Short,

    /**
     * Type of frame area disposal to be done after rendering this frame.
     * dispose_op specifies how the output buffer should be changed at the end of the delay (before rendering the next frame).
     * If the first 'fcTL' chunk uses a dispose_op of APNG_DISPOSE_OP_PREVIOUS it should be treated as APNG_DISPOSE_OP_BACKGROUND.
     */
    val dispose_op: Byte,

    /**
     * Type of frame area rendering for this frame.
     */
    val blend_op: Byte = 0,

    crc: Int
) : Chunk(offset, length, fourCC, crc), FrameChunk {

    class Parser(reader: FilterReader) : APNGParser.ChunkBodyParser {
        private val sequence_number = reader.readInt()
        private val width = reader.readInt()
        private val height = reader.readInt()
        private val x_offset = reader.readInt()
        private val y_offset = reader.readInt()
        private val delay_num = reader.readShort()
        private val delay_den = reader.readShort()
        private val dispose_op = reader.peek()
        private val blend_op = reader.peek()

        override fun toChunk(prefix: APNGParser.ChunkPrefix, crc: Int): Chunk = FCTLChunk(
            offset = prefix.offset,
            length = prefix.length,
            fourCC = prefix.fourCC,
            sequence_number = sequence_number,
            width = width,
            height = height,
            x_offset = x_offset,
            y_offset = y_offset,
            delay_num = delay_num,
            delay_den = delay_den,
            dispose_op = dispose_op,
            blend_op = blend_op,
            crc = crc
        )
    }

    companion object {
        val ID = fourCCToInt("fcTL")

        /**
         * No disposal is done on this frame before rendering the next; the contents of the output buffer are left as is.
         */
        const val APNG_DISPOSE_OP_NON: Byte = 0

        /**
         * The frame's region of the output buffer is to be cleared to fully transparent black before rendering the next frame.
         */
        const val APNG_DISPOSE_OP_BACKGROUND: Byte = 1

        /**
         * The frame's region of the output buffer is to be reverted to the previous contents before rendering the next frame.
         */
        const val APNG_DISPOSE_OP_PREVIOUS: Byte = 2
        /**
         * blend_op` specifies whether the frame is to be alpha blended into the current output buffer content,
         * or whether it should completely replace its region in the output buffer.
         ` */
        /**
         * All color components of the frame, including alpha, overwrite the current contents of the frame's output buffer region.
         */
        const val APNG_BLEND_OP_SOURCE: Byte = 0

        /**
         * The frame should be composited onto the output buffer based on its alpha,
         * using a simple OVER operation as described in the Alpha Channel Processing section of the Extensions
         * to the PNG Specification, Version 1.2.0. Note that the second variation of the sample code is applicable.
         */
        const val APNG_BLEND_OP_OVER = 1
    }
}
