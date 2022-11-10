package com.github.penfeizhou.animation.apng.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.github.penfeizhou.animation.apng.io.APNGReader
import com.github.penfeizhou.animation.apng.io.APNGWriter
import com.github.penfeizhou.animation.decode.Frame
import java.io.IOException
import java.util.zip.CRC32

class APNGFrame internal constructor(
    reader: APNGReader,
    fctlChunk: FCTLChunk,
    private val ihdrData: ByteArray,
    private val prefixChunks: MutableList<Chunk>
) : Frame<APNGReader, APNGWriter>(reader) {
    val blend_op: Byte
    val dispose_op: Byte

    internal val imageChunks: MutableList<Chunk> = mutableListOf()
    private val cRC32: CRC32
        get() {
            var crc32 = sCRC32.get()
            if (crc32 == null) {
                crc32 = CRC32()
                sCRC32.set(crc32)
            }
            return crc32
        }

    init {
        blend_op = fctlChunk.blend_op
        dispose_op = fctlChunk.dispose_op
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

    @Throws(IOException::class)
    private fun encode(apngWriter: APNGWriter): Int {
        var fileSize = 8 + 13 + 12

        //prefixChunks
        for (chunk in prefixChunks) {
            fileSize += chunk.length + 12
        }

        //imageChunks
        for (chunk in imageChunks) {
            if (chunk is IDATChunk) {
                fileSize += chunk.length + 12
            } else if (chunk is FDATChunk) {
                fileSize += chunk.length + 8
            }
        }
        fileSize += sPNGEndChunk.size
        apngWriter.reset(fileSize)
        apngWriter.putBytes(sPNGSignatures)
        //IHDR Chunk
        apngWriter.writeInt(13)
        var start = apngWriter.position()
        apngWriter.writeFourCC(IHDRChunk.ID)
        apngWriter.writeInt(frameWidth)
        apngWriter.writeInt(frameHeight)
        apngWriter.putBytes(ihdrData)
        val crc32 = cRC32
        crc32.reset()
        crc32.update(apngWriter.toByteArray(), start, 17)
        apngWriter.writeInt(crc32.value.toInt())

        //prefixChunks
        for (chunk in prefixChunks) {
            if (chunk is IENDChunk) {
                continue
            }
            reader.reset()
            reader.skip(chunk.offset.toLong())
            reader.read(apngWriter.toByteArray(), apngWriter.position(), chunk.length + 12)
            apngWriter.skip(chunk.length + 12)
        }
        //imageChunks
        for (chunk in imageChunks) {
            if (chunk is IDATChunk) {
                reader.reset()
                reader.skip(chunk.offset.toLong())
                reader.read(apngWriter.toByteArray(), apngWriter.position(), chunk.length + 12)
                apngWriter.skip(chunk.length + 12)
            } else if (chunk is FDATChunk) {
                apngWriter.writeInt(chunk.length - 4)
                start = apngWriter.position()
                apngWriter.writeFourCC(IDATChunk.ID)
                reader.reset()
                // skip to fdat data position
                reader.skip((chunk.offset + 4 + 4 + 4).toLong())
                reader.read(apngWriter.toByteArray(), apngWriter.position(), chunk.length - 4)
                apngWriter.skip(chunk.length - 4)
                crc32.reset()
                crc32.update(apngWriter.toByteArray(), start, chunk.length)
                apngWriter.writeInt(crc32.value.toInt())
            }
        }
        //endChunk
        apngWriter.putBytes(sPNGEndChunk)
        return fileSize
    }

    override fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: APNGWriter
    ): Bitmap? {
        try {
            val length = encode(writer)
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inMutable = true
            options.inBitmap = reusedBitmap
            val bytes = writer.toByteArray()
            var bitmap: Bitmap
            try {
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, length, options)
            } catch (e: IllegalArgumentException) {
                // Problem decoding into existing bitmap when on Android 4.2.2 & 4.3
                val optionsFixed = BitmapFactory.Options()
                optionsFixed.inJustDecodeBounds = false
                optionsFixed.inSampleSize = sampleSize
                optionsFixed.inMutable = true
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, length, optionsFixed)
            }
            srcRect.left = 0
            srcRect.top = 0
            srcRect.right = bitmap.width
            srcRect.bottom = bitmap.height
            dstRect.left = (frameX.toFloat() / sampleSize).toInt()
            dstRect.top = (frameY.toFloat() / sampleSize).toInt()
            dstRect.right = (frameX.toFloat() / sampleSize + bitmap.width).toInt()
            dstRect.bottom = (frameY.toFloat() / sampleSize + bitmap.height).toInt()
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
            return bitmap
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        private val sPNGSignatures = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        private val sPNGEndChunk = byteArrayOf(
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
        private val sCRC32 = ThreadLocal<CRC32>()
    }
}