package com.github.penfeizhou.animation.apng.decode

import android.content.Context
import com.github.penfeizhou.animation.apng.io.APNGReader.matchFourCC
import com.github.penfeizhou.animation.apng.io.APNGReader.readFourCC
import com.github.penfeizhou.animation.apng.io.APNGReader.readInt
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.StreamReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * @link {https://www.w3.org/TR/PNG/#5PNG-file-signature}
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
object APNGParser {
    @Suppress("unused")
    fun isAPNG(filePath: String?): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = FileInputStream(filePath)
            isAPNG(StreamReader(inputStream))
        } catch (e: Exception) {
            false
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Suppress("unused")
    fun isAPNG(context: Context, assetPath: String?): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.assets.open(assetPath!!)
            isAPNG(StreamReader(inputStream))
        } catch (e: Exception) {
            false
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Suppress("unused")
    fun isAPNG(context: Context, resId: Int): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.resources.openRawResource(resId)
            isAPNG(StreamReader(inputStream))
        } catch (e: Exception) {
            false
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @JvmStatic
    fun isAPNG(reader: Reader): Boolean {
        val apngReader = if (reader is FilterReader) reader else FilterReader(reader)
        try {
            if (!apngReader.isValid()) {
                throw FormatException()
            }
            while (apngReader.available() > 0) {
                if (parseChunk(apngReader) is ACTLChunk) {
                    return true
                }
            }
        } catch (e: IOException) {
            if (e !is FormatException) {
                e.printStackTrace()
            }
        }
        return false
    }

    @Throws(IOException::class)
    internal fun parse(reader: FilterReader): List<Chunk> {
        if (!reader.isValid()) {
            throw FormatException()
        }
        return buildList {
            while (reader.available() > 0) {
                add(parseChunk(reader))
            }
        }
    }

    private fun FilterReader.isValid(): Boolean =
        matchFourCC("\u0089PNG") && matchFourCC("\r\n\u001a\n")

    @Throws(IOException::class)
    private fun parseChunk(reader: FilterReader): Chunk {
        val offset = reader.position()
        val size = reader.readInt()
        val chunk = when (val fourCC = reader.readFourCC()) {
            ACTLChunk.ID -> ACTLChunk(offset, size, fourCC)
            FCTLChunk.ID -> FCTLChunk(offset, size, fourCC)
            FDATChunk.ID -> FDATChunk(offset, size, fourCC)
            IDATChunk.ID -> IDATChunk(offset, size, fourCC)
            IENDChunk.ID -> IENDChunk(offset, size, fourCC)
            IHDRChunk.ID -> IHDRChunk(offset, size, fourCC)
            else -> GeneralChunk(offset, size, fourCC)
        }
        chunk.parse(reader)
        chunk.crc = reader.readInt()
        return chunk
    }

    internal class FormatException : IOException("APNG Format error")
}