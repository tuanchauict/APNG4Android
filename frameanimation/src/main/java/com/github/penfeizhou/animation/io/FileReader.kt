package com.github.penfeizhou.animation.io

import java.io.File
import java.io.IOException

/**
 * @Description: FileReader
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-23
 */
class FileReader(private val file: File) : FilterReader(file.toReader()) {
    @Throws(IOException::class)
    override fun reset() {
        reader.close()
        reader = file.toReader()
    }

    companion object {
        private fun File.toReader(): Reader = StreamReader(inputStream())
    }
}
