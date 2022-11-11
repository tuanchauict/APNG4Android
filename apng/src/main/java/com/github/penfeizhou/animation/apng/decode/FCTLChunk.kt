package com.github.penfeizhou.animation.apng.decode

import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.apng.io.APNGReader.readShort
import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException

/**
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 * @see {link=https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG..27fcTL.27:_The_Frame_Control_Chunk}
 */
internal class FCTLChunk(offset: Int, length: Int, fourCC: Int) : Chunk(offset, length, fourCC) {
    var sequence_number = 0
    /**
     * x_offset >= 0
     * y_offset >= 0
     * width > 0
     * height > 0
     * x_offset + width <= 'IHDR' width
     * y_offset + height <= 'IHDR' height
     */
    /**
     * Width of the following frame.
     */
    var width = 0

    /**
     * Height of the following frame.
     */
    var height = 0

    /**
     * X position at which to render the following frame.
     */
    var x_offset = 0

    /**
     * Y position at which to render the following frame.
     */
    var y_offset = 0
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
    var delay_num: Short = 0

    /**
     * Frame delay fraction denominator.
     */
    var delay_den: Short = 0

    /**
     * Type of frame area disposal to be done after rendering this frame.
     * dispose_op specifies how the output buffer should be changed at the end of the delay (before rendering the next frame).
     * If the first 'fcTL' chunk uses a dispose_op of APNG_DISPOSE_OP_PREVIOUS it should be treated as APNG_DISPOSE_OP_BACKGROUND.
     */
    var dispose_op: Byte = 0

    /**
     * Type of frame area rendering for this frame.
     */
    var blend_op: Byte = 0

    @Throws(IOException::class)
    override fun innerParse(reader: FilterReader) {
        sequence_number = reader.readInt()
        width = reader.readInt()
        height = reader.readInt()
        x_offset = reader.readInt()
        y_offset = reader.readInt()
        delay_num = reader.readShort()
        delay_den = reader.readShort()
        dispose_op = reader.peek()
        blend_op = reader.peek()
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
