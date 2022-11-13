package com.github.penfeizhou.animation.apng.decode

import android.text.TextUtils
import com.github.penfeizhou.animation.io.FilterReader
import java.io.IOException
import kotlin.Throws

/**
 * Length 4 bytes Specifies the length of the data field in the data block, which should not exceed (231-1) bytes
 * Chunk Type Code 4 bytes The Chunk Type Code consists of ASCII letters (A-Z and a-z).
 * Chunk Data Variable length Stores the data specified by the Chunk Type Code
 * CRC (Cyclic Redundancy Check) 4 bytes Stores the cyclic redundancy code used to check for errors
 * @Link https://www.w3.org/TR/PNG
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal sealed class Chunk(val offset: Long, val length: Int, val fourCC: Int) {
    var crc = 0

    @Throws(IOException::class)
    fun parse(reader: FilterReader) {
        val available = reader.available()
        innerParse(reader)
        val offset = available - reader.available()
        if (offset > length) {
            throw IOException("Out of chunk area")
        } else if (offset < length) {
            reader.skip((length - offset).toLong())
        }
    }

    @Throws(IOException::class)
    open fun innerParse(reader: FilterReader) = Unit

    companion object {
        @JvmStatic
        fun fourCCToInt(fourCC: String): Int =
            if (TextUtils.isEmpty(fourCC) || fourCC.length != 4) {
                -0x45210001
            } else {
                (
                    fourCC[0].code and 0xff
                        or (fourCC[1].code and 0xff shl 8)
                        or (fourCC[2].code and 0xff shl 16)
                        or (fourCC[3].code and 0xff shl 24)
                    )
            }
    }
}

internal class FramePrefixChunk(offset: Long, length: Int, fourCC: Int) : Chunk(offset, length, fourCC)

internal sealed interface FrameChunk

internal sealed interface DATChunk {
    val length: Int
}
