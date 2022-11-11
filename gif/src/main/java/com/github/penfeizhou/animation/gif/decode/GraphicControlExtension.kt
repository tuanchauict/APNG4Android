package com.github.penfeizhou.animation.gif.decode

import com.github.penfeizhou.animation.gif.io.GifReader.readUInt16
import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException

/**
 * @Description: GraphicControlExtension
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-17
 */
class GraphicControlExtension : ExtensionBlock() {
    private var blockSize = 0
    private var packedFields: Byte = 0

    @JvmField
    var delayTime = 0

    @JvmField
    var transparentColorIndex = 0

    @Throws(IOException::class)
    override fun receive(reader: FilterReader) {
        blockSize = reader.peek().toInt() and 0xff
        packedFields = reader.peek()
        delayTime = reader.readUInt16()
        transparentColorIndex = reader.peek().toInt() and 0xff
        if (reader.peek().toInt() != 0) {
            throw GifParser.FormatException()
        }
    }

    /**
     * Values :
     * 0 -   No disposal specified. The decoder is
     * not required to take any action.
     * 1 -   Do not dispose. The graphic is to be left
     * in place.
     * 2 -   Restore to background color. The area used by the
     * graphic must be restored to the background color.
     * 3 -   Restore to previous. The decoder is required to
     * restore the area overwritten by the graphic with
     * what was there prior to rendering the graphic.
     * 4-7 -    To be defined.
     */
    fun disposalMethod(): Int {
        return packedFields.toInt() shr 2 and 0x7
    }

    /**
     * User Input Flag - Indicates whether or not user input is
     * expected before continuing. If the flag is set, processing will
     * continue when user input is entered. The nature of the User input
     * is determined by the application (Carriage Return, Mouse Button
     * Click, etc.).
     *
     *
     * Values :
     * 0 -   User input is not expected.
     * 1 -   User input is expected.
     *
     *
     * When a Delay Time is used and the User Input Flag is set,
     * processing will continue when user input is received or when the
     * delay time expires, whichever occurs first.
     */
    fun userInputFlag(): Boolean {
        return packedFields.toInt() and 0x2 == 0x2
    }

    /**
     * When a Delay Time is used and the User Input Flag is set,
     * processing will continue when user input is received or when the
     * delay time expires, whichever occurs first.
     *
     *
     * vi) Transparency Flag - Indicates whether a transparency index is
     * given in the Transparent Index field. (This field is the least
     * significant bit of the byte.)
     *
     *
     * Values :
     * 0 -   Transparent Index is not given.
     * 1 -   Transparent Index is given.
     */
    fun transparencyFlag(): Boolean {
        return packedFields.toInt() and 0x1 == 0x1
    }

    override fun size(): Int {
        return blockSize + 1
    }
}
