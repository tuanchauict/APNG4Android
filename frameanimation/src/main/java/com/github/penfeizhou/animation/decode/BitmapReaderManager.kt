package com.github.penfeizhou.animation.decode

import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException

internal class BitmapReaderManager<R : Reader>(
    private val loader: Loader,
    private val readerFactory: (Reader) -> R
) {
    private var reader: R? = null

    @Throws(IOException::class)
    fun getReader(): R {
        val localReader = reader
        if (localReader != null) {
            localReader.reset()
            return localReader
        }
        reader = readerFactory(loader.obtain())
        return getReader()
    }

    @Throws(IOException::class)
    fun closeReader() {
        try {
            reader?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        reader = null
    }
}