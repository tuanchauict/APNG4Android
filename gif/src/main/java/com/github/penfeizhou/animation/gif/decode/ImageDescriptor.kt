package com.github.penfeizhou.animation.gif.decode

import com.github.penfeizhou.animation.gif.io.GifReader
import java.io.IOException

/**
 * Image Descriptor.
 * a. Description. Each image in the Data Stream is composed of an Image
 * Descriptor, an optional Local Color Table, and the image data.  Each
 * image must fit within the boundaries of the Logical Screen, as defined
 * in the Logical Screen Descriptor.
 *
 *
 * The Image Descriptor contains the parameters necessary to process a table
 * based image. The coordinates given in this block refer to coordinates
 * within the Logical Screen, and are given in pixels. This block is a
 * Graphic-Rendering Block, optionally preceded by one or more Control
 * blocks such as the Graphic Control Extension, and may be optionally
 * followed by a Local Color Table; the Image Descriptor is always followed
 * by the image data.
 *
 *
 * This block is REQUIRED for an image.  Exactly one Image Descriptor must
 * be present per image in the Data Stream.  An unlimited number of images
 * may be present per Data Stream.
 *
 *
 * b. Required Version.  87a.
 *
 *
 * c. Syntax.
 *
 *
 * 7 6 5 4 3 2 1 0        Field Name                    Type
 * +---------------+
 * 0  |               |       Image Separator               Byte
 * +---------------+
 * 1  |               |       Image Left Position           Unsigned
 * +-             -+
 * 2  |               |
 * +---------------+
 * 3  |               |       Image Top Position            Unsigned
 * +-             -+
 * 4  |               |
 * +---------------+
 * 5  |               |       Image Width                   Unsigned
 * +-             -+
 * 6  |               |
 * +---------------+
 * 7  |               |       Image Height                  Unsigned
 * +-             -+
 * 8  |               |
 * +---------------+
 * 9  | | | |   |     |       <Packed Fields>               See below
 * +---------------+
</Packed> *
 *
 * <Packed Fields>  =      Local Color Table Flag        1 Bit
 * Interlace Flag                1 Bit
 * Sort Flag                     1 Bit
 * Reserved                      2 Bits
 * Size of Local Color Table     3 Bits
</Packed> *
 *
 * i) Image Separator - Identifies the beginning of an Image
 * Descriptor. This field contains the fixed value 0x2C.
 *
 *
 * ii) Image Left Position - Column number, in pixels, of the left edge
 * of the image, with respect to the left edge of the Logical Screen.
 * Leftmost column of the Logical Screen is 0.
 *
 *
 * iii) Image Top Position - Row number, in pixels, of the top edge of
 * the image with respect to the top edge of the Logical Screen. Top
 * row of the Logical Screen is 0.
 *
 *
 * iv) Image Width - Width of the image in pixels.
 *
 *
 * v) Image Height - Height of the image in pixels.
 *
 *
 * vi) Local Color Table Flag - Indicates the presence of a Local Color
 * Table immediately following this Image Descriptor. (This field is
 * the most significant bit of the byte.)
 *
 *
 *
 *
 * Values :    0 -   Local Color Table is not present. Use
 * Global Color Table if available.
 * 1 -   Local Color Table present, and to follow
 * immediately after this Image Descriptor.
 *
 *
 * vii) Interlace Flag - Indicates if the image is interlaced. An image
 * is interlaced in a four-pass interlace pattern; see Appendix E for
 * details.
 *
 *
 * Values :    0 - Image is not interlaced.
 * 1 - Image is interlaced.
 *
 *
 * viii) Sort Flag - Indicates whether the Local Color Table is
 * sorted.  If the flag is set, the Local Color Table is sorted, in
 * order of decreasing importance. Typically, the order would be
 * decreasing frequency, with most frequent color first. This assists
 * a decoder, with fewer available colors, in choosing the best subset
 * of colors; the decoder may use an initial segment of the table to
 * render the graphic.
 *
 *
 * Values :    0 -   Not ordered.
 * 1 -   Ordered by decreasing importance, most
 * important color first.
 *
 *
 * ix) Size of Local Color Table - If the Local Color Table Flag is
 * set to 1, the value in this field is used to calculate the number
 * of bytes contained in the Local Color Table. To determine that
 * actual size of the color table, raise 2 to the value of the field
 * + 1. This value should be 0 if there is no Local Color Table
 * specified. (This field is made up of the 3 least significant bits
 * of the byte.)
 *
 *
 * d. Extensions and Scope. The scope of this block is the Table-based Image
 * Data Block that follows it. This block may be modified by the Graphic
 * Control Extension.
 *
 *
 * e. Recommendation. None.
 *
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-16
 */
class ImageDescriptor : Block {
    @JvmField
    var frameX = 0
    @JvmField
    var frameY = 0
    @JvmField
    var frameWidth = 0
    @JvmField
    var frameHeight = 0
    private var flag: Byte = 0
    @JvmField
    var localColorTable: ColorTable? = null
    @JvmField
    var lzwMinimumCodeSize = 0
    @JvmField
    var imageDataOffset = 0
    @Throws(IOException::class)
    override fun receive(reader: GifReader) {
        frameX = reader.readUInt16()
        frameY = reader.readUInt16()
        frameWidth = reader.readUInt16()
        frameHeight = reader.readUInt16()
        flag = reader.peek()
        if (localColorTableFlag()) {
            localColorTable = ColorTable(localColorTableSize())
            localColorTable!!.receive(reader)
        }
        lzwMinimumCodeSize = reader.peek().toInt() and 0xff
        imageDataOffset = reader.position()
        var blockSize: Byte
        while (reader.peek().also { blockSize = it }.toInt() != 0x0) {
            reader.skip((blockSize.toInt() and 0xff).toLong())
        }
    }

    fun localColorTableFlag(): Boolean {
        return flag.toInt() and 0x80 == 0x80
    }

    fun interlaceFlag(): Boolean {
        return flag.toInt() and 0x40 == 0x40
    }

    fun sortFlag(): Boolean {
        return flag.toInt() and 0x20 == 0x20
    }

    fun localColorTableSize(): Int {
        return 2 shl (flag.toInt() and 0xf)
    }

    override fun size(): Int {
        return 0
    }
}