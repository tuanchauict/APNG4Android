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
    internal fun parse(reader: FilterReader): ParseChunkResult {
        if (!reader.isValid()) {
            throw FormatException()
        }
        val frameDatas = mutableListOf<FrameData>()
        val prefixChunks = mutableListOf<FramePrefixChunk>()
        var ihdrChunk = IHDRChunk.DUMMY
        var actlChunk: ACTLChunk? = null
        var hasIDATChunk = false
        while (reader.available() > 0) {
            when (val chunk = parseChunk(reader)) {
                is FCTLChunk -> frameDatas.add(FrameData(chunk))
                is FDATChunk -> frameDatas.lastOrNull()?.imageChunks?.add(chunk)
                is IDATChunk -> {
                    hasIDATChunk = true
                    frameDatas.lastOrNull()?.imageChunks?.add(chunk)
                }
                is FramePrefixChunk -> prefixChunks.add(chunk)
                is IHDRChunk -> ihdrChunk = chunk
                is ACTLChunk -> actlChunk = chunk
                is IENDChunk -> Unit
            }
        }
        return ParseChunkResult(frameDatas, prefixChunks, ihdrChunk, actlChunk, hasIDATChunk)
    }

    private fun FilterReader.isValid(): Boolean =
        matchFourCC("\u0089PNG") && matchFourCC("\r\n\u001a\n")

    @Throws(IOException::class)
    private fun parseChunk(reader: FilterReader): Chunk {
        val prefix = ChunkPrefix(
            offset = reader.position().toLong(),
            length = reader.readInt(),
            fourCC = reader.readFourCC()
        )
        return parseChunkDetail(reader, prefix)
    }

    private fun parseChunkDetail(reader: FilterReader, prefix: ChunkPrefix): Chunk {
        val available = reader.available()
        val chunkBody = when (prefix.fourCC) {
            ACTLChunk.ID -> ACTLChunk.Parser(reader)
            FCTLChunk.ID -> FCTLChunk.Parser(reader)
            FDATChunk.ID -> FDATChunk.Parser(reader)
            IDATChunk.ID -> IDATChunk.Parser
            IENDChunk.ID -> IENDChunk.Parser
            IHDRChunk.ID -> IHDRChunk.Parser(reader)
            else -> FramePrefixChunk.Parser
        }
        val offset = available - reader.available()
        when {
            offset > prefix.length -> throw IOException("Out of chunk area")
            offset < prefix.length -> reader.skip(prefix.length.toLong() - offset)
        }
        val crc = reader.readInt()

        return chunkBody.toChunk(prefix, crc)
    }

    internal class ChunkPrefix(val offset: Long, val length: Int, val fourCC: Int)

    /**
     * A parser of chunk body.
     * The order of attributes in the child classes are by purposed, DO NOT CHANGE THE ORDER.
     */
    internal sealed interface ChunkBodyParser {
        fun toChunk(prefix: ChunkPrefix, crc: Int): Chunk
    }

    internal class FormatException : IOException("APNG Format error")

    internal class ParseChunkResult(
        val frameDatas: List<FrameData>,
        val prefixChunks: List<FramePrefixChunk>,
        val ihdrChunk: IHDRChunk,
        val actlChunk: ACTLChunk?,
        val hasIDATChunk: Boolean
    )

    internal class FrameData(val fctlChunk: FCTLChunk) {
        val imageChunks: MutableList<DATChunk> = mutableListOf()
    }
}
