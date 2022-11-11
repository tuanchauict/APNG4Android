package com.github.penfeizhou.animation.webp.decode

import android.content.Context
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.StreamReader
import com.github.penfeizhou.animation.webp.io.WebPReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-11
 */
object WebPParser {
    fun isAWebP(filePath: String?): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = FileInputStream(filePath)
            isAWebP(StreamReader(inputStream))
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

    fun isAWebP(context: Context, assetPath: String?): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.assets.open(assetPath!!)
            isAWebP(StreamReader(inputStream))
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

    fun isAWebP(context: Context, resId: Int): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.resources.openRawResource(resId)
            isAWebP(StreamReader(inputStream))
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
    fun isAWebP(`in`: Reader?): Boolean {
        val reader = if (`in` is WebPReader) `in` else WebPReader(`in`!!)
        try {
            if (!reader.matchFourCC("RIFF")) {
                return false
            }
            reader.skip(4)
            if (!reader.matchFourCC("WEBP")) {
                return false
            }
            while (reader.available() > 0) {
                val chunk = parseChunk(reader)
                if (chunk is VP8XChunk) {
                    return chunk.animation()
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
    fun parse(reader: WebPReader): List<BaseChunk> {
        //@link {https://developers.google.com/speed/webp/docs/riff_container#webp_file_header}
        if (!reader.matchFourCC("RIFF")) {
            throw FormatException()
        }
        reader.skip(4)
        if (!reader.matchFourCC("WEBP")) {
            throw FormatException()
        }
        val chunks: MutableList<BaseChunk> = ArrayList()
        while (reader.available() > 0) {
            chunks.add(parseChunk(reader))
        }
        return chunks
    }

    @Throws(IOException::class)
    fun parseChunk(reader: WebPReader): BaseChunk {
        //@link {https://developers.google.com/speed/webp/docs/riff_container#riff_file_format}
        val offset = reader.position()
        val chunkFourCC = reader.readFourCC()
        val chunkSize = reader.readUInt32()
        val chunk: BaseChunk
        chunk = if (VP8XChunk.ID == chunkFourCC) {
            VP8XChunk()
        } else if (ANIMChunk.ID == chunkFourCC) {
            ANIMChunk()
        } else if (ANMFChunk.ID == chunkFourCC) {
            ANMFChunk()
        } else if (ALPHChunk.ID == chunkFourCC) {
            ALPHChunk()
        } else if (VP8Chunk.ID == chunkFourCC) {
            VP8Chunk()
        } else if (VP8LChunk.ID == chunkFourCC) {
            VP8LChunk()
        } else if (ICCPChunk.ID == chunkFourCC) {
            ICCPChunk()
        } else if (XMPChunk.ID == chunkFourCC) {
            XMPChunk()
        } else if (EXIFChunk.ID == chunkFourCC) {
            EXIFChunk()
        } else {
            BaseChunk()
        }
        chunk.chunkFourCC = chunkFourCC
        chunk.payloadSize = chunkSize
        chunk.offset = offset
        chunk.parse(reader)
        return chunk
    }

    internal class FormatException : IOException("WebP Format error")
}