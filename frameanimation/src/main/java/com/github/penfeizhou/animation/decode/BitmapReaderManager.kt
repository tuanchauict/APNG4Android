package com.github.penfeizhou.animation.decode

import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException

internal class BitmapReaderManager(private val loader: Loader) {
    private var reader: FilterReader? = null

    @Throws(IOException::class)
    fun getReader(): FilterReader {
        val localReader = reader
        if (localReader != null) {
            localReader.reset()
            return localReader
        }
        reader = FilterReader(loader.obtain())
        return getReader()
    }

    fun closeReader() {
        try {
            reader?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        reader = null
    }
}
