package com.github.penfeizhou.animation.gif.decode

import com.github.penfeizhou.animation.gif.io.GifReader
import java.io.IOException

/**
 * @Description: ApplicationExtension
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-17
 */
class ApplicationExtension : ExtensionBlock() {
    var loopCount = -1
    var identifier: String? = null
    @Throws(IOException::class)
    override fun receive(reader: GifReader) {
        val blockSize = reader.peek().toInt()
        val stringBuilder = StringBuilder()
        for (i in 0 until blockSize) {
            stringBuilder.append(Char(reader.peek().toUShort()))
        }
        identifier = stringBuilder.toString()
        if ("NETSCAPE2.0" == identifier) {
            val size = reader.peek().toInt() and 0xff
            if (size == 3 && reader.peek().toInt() and 0xff == 1) {
                loopCount = reader.readUInt16()
            }
            var dataSubBlock: DataSubBlock
            do {
                dataSubBlock = DataSubBlock.retrieve(reader)
            } while (!dataSubBlock.isTerminal)
        } else {
            var dataSubBlock: DataSubBlock
            do {
                dataSubBlock = DataSubBlock.retrieve(reader)
            } while (!dataSubBlock.isTerminal)
        }
    }

    override fun size(): Int {
        return 0
    }
}