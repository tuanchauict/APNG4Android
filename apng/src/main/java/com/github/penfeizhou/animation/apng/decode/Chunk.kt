package com.github.penfeizhou.animation.apng.decode

import kotlin.Throws
import com.github.penfeizhou.animation.apng.io.APNGReader
import android.text.TextUtils
import java.io.IOException

/**
 * @Description: Length (长度)	4字节	指定数据块中数据域的长度，其长度不超过(231－1)字节
 * Chunk Type Code (数据块类型码)	4字节	数据块类型码由ASCII字母(A-Z和a-z)组成
 * Chunk Data (数据块数据)	可变长度	存储按照Chunk Type Code指定的数据
 * CRC (循环冗余检测)	4字节	存储用来检测是否有错误的循环冗余码
 * @Link https://www.w3.org/TR/PNG
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
internal open class Chunk {
    var length = 0
    var fourcc = 0
    var crc = 0
    var offset = 0

    @Throws(IOException::class)
    fun parse(reader: APNGReader) {
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
    open fun innerParse(reader: APNGReader) = Unit

    companion object {
        @JvmStatic
        fun fourCCToInt(fourCC: String): Int {
            return if (TextUtils.isEmpty(fourCC) || fourCC.length != 4) {
                -0x45210001
            } else (fourCC[0].code and 0xff
                    or (fourCC[1].code and 0xff shl 8
                    ) or (fourCC[2].code and 0xff shl 16
                    ) or (fourCC[3].code and 0xff shl 24))
        }
    }
}