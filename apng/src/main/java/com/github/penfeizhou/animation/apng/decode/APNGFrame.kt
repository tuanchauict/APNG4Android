package com.github.penfeizhou.animation.apng.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import java.io.IOException
import java.util.zip.CRC32
import kotlin.concurrent.getOrSet

class APNGFrame internal constructor(
    private val reader: FilterReader,
    fctlChunk: FCTLChunk,
    private val ihdrData: ByteArray,
    private val prefixChunks: MutableList<Chunk>
) : Frame() {
    val blendOp: Byte = fctlChunk.blend_op
    val disposeOp: Byte = fctlChunk.dispose_op

    internal val imageChunks: MutableList<DATChunk> = mutableListOf()

    init {
        frameDuration =
            fctlChunk.delay_num * 1000 / if (fctlChunk.delay_den.toInt() == 0) 100 else fctlChunk.delay_den
        if (frameDuration < 10) {
            /*  Many annoying ads specify a 0 duration to make an image flash as quickly as  possible.
            We follow Safari and Firefox's behavior and use a duration of 100 ms for any frames that specify a duration of <= 10 ms.
            See <rdar://problem/7689300> and <http://webkit.org/b/36082> for more information.
            See also: http://nullsleep.tumblr.com/post/16524517190/animated-gif-minimum-frame-delay-browser.
            */
            frameDuration = 100
        }
        frameWidth = fctlChunk.width
        frameHeight = fctlChunk.height
        frameX = fctlChunk.x_offset
        frameY = fctlChunk.y_offset
    }

    override fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: Writer
    ): Bitmap? {
        try {
            val length = encode(writer)
            val bytes = writer.toByteArray()
            val options = createBitmapFactoryOptions(sampleSize, reusedBitmap)
            val bitmap: Bitmap = try {
                BitmapFactory.decodeByteArray(bytes, 0, length, options)
            } catch (e: IllegalArgumentException) {
                // Problem decoding into existing bitmap when on Android 4.2.2 & 4.3
                val optionsFixed = createBitmapFactoryOptions(sampleSize, null)
                BitmapFactory.decodeByteArray(bytes, 0, length, optionsFixed)
            }

            srcRect.set(0, 0, bitmap.width, bitmap.height)

            val destLeft = frameX / sampleSize
            val destTop = frameY / sampleSize
            dstRect.set(destLeft, destTop, destLeft + bitmap.width, destTop + bitmap.height)
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
            return bitmap
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun createBitmapFactoryOptions(
        sampleSize: Int,
        reusedBitmap: Bitmap?
    ): BitmapFactory.Options = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sampleSize
        inMutable = true
        inBitmap = reusedBitmap
    }

    @Throws(IOException::class)
    private fun encode(writer: Writer): Int {
        var fileSize = 8 + 13 + 12

        //prefixChunks
        for (chunk in prefixChunks) {
            fileSize += chunk.length + 12
        }

        //imageChunks
        for (chunk in imageChunks) {
            fileSize += when (chunk) {
                is IDATChunk -> chunk.length + 12
                is FDATChunk -> chunk.length + 8
            }
        }

        fileSize += PNG_END_CHUNK.size

        writer.reset(fileSize)
        writer.putBytes(PNG_SIGNATURES)

        //IHDR Chunk
        writer.writeInt(13)
        var start = writer.position()
        writer.writeFourCC(IHDRChunk.ID)
        writer.writeInt(frameWidth)
        writer.writeInt(frameHeight)
        writer.putBytes(ihdrData)
        val crc32 = CRC32_THREAD_LOCAL.getOrSet { CRC32() }
        crc32.reset()
        crc32.update(writer.toByteArray(), start, 17)
        writer.writeInt(crc32.value.toInt())

        //prefixChunks
        for (chunk in prefixChunks) {
            if (chunk is IENDChunk) {
                continue
            }
            reader.reset()
            reader.skip(chunk.offset.toLong())
            reader.read(writer.toByteArray(), writer.position(), chunk.length + 12)
            writer.skip(chunk.length + 12)
        }
        //imageChunks
        for (chunk in imageChunks) {
            when (chunk) {
                is IDATChunk -> {
                    reader.reset()
                    reader.skip(chunk.offset.toLong())
                    reader.read(writer.toByteArray(), writer.position(), chunk.length + 12)
                    writer.skip(chunk.length + 12)
                }
                is FDATChunk -> {
                    writer.writeInt(chunk.length - 4)
                    start = writer.position()
                    writer.writeFourCC(IDATChunk.ID)
                    reader.reset()
                    // skip to fdat data position
                    reader.skip((chunk.offset + 4 + 4 + 4).toLong())
                    reader.read(writer.toByteArray(), writer.position(), chunk.length - 4)
                    writer.skip(chunk.length - 4)
                    crc32.reset()
                    crc32.update(writer.toByteArray(), start, chunk.length)
                    writer.writeInt(crc32.value.toInt())
                }
            }
        }
        //endChunk
        writer.putBytes(PNG_END_CHUNK)
        return fileSize
    }

    private fun Writer.writeFourCC(value: Int) {
        putByte((value and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
        putByte((value shr 24 and 0xff).toByte())
    }

    private fun Writer.writeInt(value: Int) {
        putByte((value shr 24 and 0xff).toByte())
        putByte((value shr 16 and 0xff).toByte())
        putByte((value shr 8 and 0xff).toByte())
        putByte((value and 0xff).toByte())
    }

    companion object {
        private val PNG_SIGNATURES = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        private val PNG_END_CHUNK = byteArrayOf(
            0,
            0,
            0,
            0,
            0x49,
            0x45,
            0x4E,
            0x44,
            0xAE.toByte(),
            0x42,
            0x60,
            0x82.toByte()
        )
        private val CRC32_THREAD_LOCAL = ThreadLocal<CRC32>()
    }
}