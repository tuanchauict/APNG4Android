package com.github.penfeizhou.animation.gif.decode

import com.github.penfeizhou.animation.gif.io.GifReader.readUInt16
import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-17
 */
class PlaintTextExtension : ExtensionBlock() {
    private val plainTextData: MutableList<DataSubBlock> = ArrayList()
    @Throws(IOException::class)
    override fun receive(reader: FilterReader) {
        val blockSize = reader.peek().toInt()
        val x = reader.readUInt16()
        val y = reader.readUInt16()
        val width = reader.readUInt16()
        val height = reader.readUInt16()
        val characterCellWidth = reader.peek().toInt()
        val characterCellHeight = reader.peek().toInt()
        val fgColorIndex = reader.peek().toInt()
        val bgColorIndex = reader.peek().toInt()
        var dataSubBlock: DataSubBlock
        while (!DataSubBlock.retrieve(reader).also { dataSubBlock = it }.isTerminal) {
            plainTextData.add(dataSubBlock)
        }
    }

    override fun size(): Int = 0
}