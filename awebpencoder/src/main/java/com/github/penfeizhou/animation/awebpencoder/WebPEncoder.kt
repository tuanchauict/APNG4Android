package com.github.penfeizhou.animation.awebpencoder

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.annotation.WorkerThread
import com.github.penfeizhou.animation.decode.FrameSeqDecoder2
import com.github.penfeizhou.animation.gif.decode.ApplicationExtension
import com.github.penfeizhou.animation.gif.decode.ColorTable
import com.github.penfeizhou.animation.gif.decode.GifFrame
import com.github.penfeizhou.animation.gif.decode.GifParser
import com.github.penfeizhou.animation.gif.decode.GraphicControlExtension
import com.github.penfeizhou.animation.gif.decode.ImageDescriptor
import com.github.penfeizhou.animation.gif.decode.LogicalScreenDescriptor
import com.github.penfeizhou.animation.gif.io.GifWriter
import com.github.penfeizhou.animation.io.ByteBufferReader
import com.github.penfeizhou.animation.io.ByteBufferWriter
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import com.github.penfeizhou.animation.webp.decode.BaseChunk
import com.github.penfeizhou.animation.webp.decode.ICCPChunk
import com.github.penfeizhou.animation.webp.decode.VP8XChunk
import com.github.penfeizhou.animation.webp.decode.WebPParser
import com.github.penfeizhou.animation.webp.io.WebPWriter.put1Based
import com.github.penfeizhou.animation.webp.io.WebPWriter.putFourCC
import com.github.penfeizhou.animation.webp.io.WebPWriter.putUInt16
import com.github.penfeizhou.animation.webp.io.WebPWriter.putUInt24
import com.github.penfeizhou.animation.webp.io.WebPWriter.putUInt32
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * @Description: com.github.penfeizhou.animation.awebpencoder
 * @Author: pengfei.zhou
 * @CreateDate: 2019-08-02
 */
class WebPEncoder {
    private var bgColor = 0
    private var loopCount = 0
    private val writer = ByteBufferWriter()
    private val frameInfoList: MutableList<FrameInfo> = ArrayList()
    private var quality = 80
    private val outputStream = ByteArrayOutputStream()
    private var width = 0
    private var height = 0
    private fun loadDecoder(decoder: FrameSeqDecoder2) {
        decoder.getBounds()
        val frameCount = decoder.frameCount
        val delay: MutableList<Int> = ArrayList()
        for (i in 0 until frameCount) {
            delay.add(decoder.getFrame(i)!!.frameDuration)
        }
        for (i in 0 until frameCount) {
            try {
                val bitmap = decoder.getFrameBitmap(i)
                val frameInfo = FrameBuilder()
                    .bitmap(bitmap).offsetX(0).offsetY(0).duration(delay[i])
                    .blending(false).disposal(true)
                    .build()
                addFrame(frameInfo)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun loadGif(loader: Loader) {
        try {
            val reader = FilterReader(loader.obtain())
            val blocks = GifParser.parse(reader)
            var globalColorTable: ColorTable? = null
            val frames: MutableList<GifFrame> = ArrayList()
            var graphicControlExtension: GraphicControlExtension? = null
            var bgColorIndex = -1
            for (block in blocks) {
                if (block is LogicalScreenDescriptor) {
                    width = block.screenWidth
                    height = block.screenHeight
                    if (block.gColorTableFlag()) {
                        bgColorIndex = block.bgColorIndex.toInt() and 0xff
                    }
                } else if (block is ColorTable) {
                    globalColorTable = block
                } else if (block is GraphicControlExtension) {
                    graphicControlExtension = block
                } else if (block is ImageDescriptor) {
                    val gifFrame =
                        GifFrame(reader, globalColorTable, graphicControlExtension, block)
                    frames.add(gifFrame)
                } else if (block is ApplicationExtension && "NETSCAPE2.0" == block.identifier) {
                    loopCount = block.loopCount
                }
            }
            if (globalColorTable != null && bgColorIndex > 0) {
                val abgr = globalColorTable.colorTable[bgColorIndex]
                bgColor = Color.rgb(abgr and 0xff, abgr shr 8 and 0xff, abgr shr 16 and 0xff)
            }
            val writer = GifWriter()
            for (frame in frames) {
                writer.reset(frame.frameWidth * frame.frameHeight)
                val pixels = writer.asIntArray()
                frame.encode(pixels, 1)
                val bitmap = Bitmap.createBitmap(
                    frame.frameWidth,
                    frame.frameHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(writer.asIntBuffer().rewind())
                val frameBuilder = FrameBuilder()
                var disposal = false
                var blending = false
                when (frame.disposalMethod) {
                    0, 1 -> {
                        disposal = false
                        blending = true
                    }
                    2 -> {
                        disposal = true
                        blending = true
                    }
                    3 -> {
                        disposal = true
                        blending = true
                    }
                }
                frameBuilder
                    .bitmap(bitmap)
                    .duration(frame.frameDuration)
                    .offsetX(frame.frameX)
                    .offsetY(frame.frameY)
                    .disposal(disposal)
                    .blending(blending)
                frameInfoList.add(frameBuilder.build())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Suppress("unused")
    fun loopCount(loopCount: Int): WebPEncoder {
        this.loopCount = loopCount
        return this
    }

    fun addFrame(frameInfo: FrameInfo): WebPEncoder {
        frameInfoList.add(frameInfo)
        width = width.coerceAtLeast(frameInfo.bitmap!!.width)
        height = height.coerceAtLeast(frameInfo.bitmap!!.height)
        return this
    }

    @Suppress("unused")
    fun addFrame(bitmap: Bitmap, frameX: Int, frameY: Int, duration: Int): WebPEncoder {
        val frameInfo = FrameInfo()
        frameInfo.bitmap = bitmap
        frameInfo.frameX = frameX
        frameInfo.frameY = frameY
        frameInfo.duration = duration
        frameInfoList.add(frameInfo)
        width = width.coerceAtLeast(bitmap.width)
        height = height.coerceAtLeast(bitmap.height)
        return this
    }

    @Suppress("unused")
    fun quality(quality: Int): WebPEncoder {
        this.quality = quality
        return this
    }

    @WorkerThread
    fun build(): ByteArray {
        // 10M
        writer.reset(1000 * 1000 * 10)
        val vp8xPayloadSize = 10
        var size = 4

        // header
        writer.putFourCC("RIFF")
        writer.putUInt32(size)
        writer.putFourCC("WEBP")

        // VP8X
        writer.putFourCC("VP8X")
        writer.putUInt32(vp8xPayloadSize)
        writer.putByte((0x10 or 0x2).toByte())
        writer.putUInt24(0)
        writer.put1Based(width)
        writer.put1Based(height)
        // ANIM
        writer.putFourCC("ANIM")
        writer.putUInt32(6)
        writer.putUInt32(bgColor)
        writer.putUInt16(loopCount)

        // ANMF
        for (frameInfo in frameInfoList) {
            encodeFrame(frameInfo)
        }
        val bytes = writer.toByteArray()
        size = writer.position() - 8
        bytes[4] = (size and 0xff).toByte()
        bytes[5] = (size shr 8 and 0xff).toByte()
        bytes[6] = (size shr 16 and 0xff).toByte()
        bytes[7] = (size shr 24 and 0xff).toByte()
        val ret = ByteBuffer.allocate(writer.position())
        ret.put(bytes, 0, writer.position())
        return ret.array()
    }

    private fun encodeFrame(frameInfo: FrameInfo): Int {
        outputStream.reset()
        if (!frameInfo.bitmap!!.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)) {
            Log.e(TAG, "error in encode frame")
            return 0
        }
        val byteBuffer = ByteBuffer.wrap(outputStream.toByteArray(), 0, outputStream.size())
        val reader = FilterReader(ByteBufferReader(byteBuffer))
        try {
            val chunks = WebPParser.parse(reader)
            var payLoadSize = 16
            var width = frameInfo.bitmap!!.width
            var height = frameInfo.bitmap!!.height
            for (chunk in chunks) {
                if (chunk is VP8XChunk) {
                    width = chunk.canvasWidth
                    height = chunk.canvasHeight
                    continue
                }
                if (chunk is ICCPChunk) {
                    continue
                }
                payLoadSize += chunk.payloadSize + 8
                payLoadSize += payLoadSize and 1
            }
            writer.putUInt32(BaseChunk.fourCCToInt("ANMF"))
            writer.putUInt32(payLoadSize)
            writer.putUInt24(frameInfo.frameX / 2)
            writer.putUInt24(frameInfo.frameY / 2)
            writer.put1Based(width)
            writer.put1Based(height)
            writer.putUInt24(frameInfo.duration)
            writer.putByte(((if (frameInfo.blending) 0x2 else 0) or if (frameInfo.disposal) 0x1 else 0).toByte())
            for (chunk in chunks) {
                if (chunk is VP8XChunk) {
                    continue
                }
                if (chunk is ICCPChunk) {
                    continue
                }
                writeChunk(writer, reader, chunk)
            }
            return payLoadSize
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "error in encode frame")
        }
        return 0
    }

    @Suppress("unused")
    fun backgroundColor(backgroundColor: Int): WebPEncoder {
        bgColor = backgroundColor
        return this
    }

    @Throws(IOException::class)
    private fun writeChunk(writer: Writer, reader: FilterReader, chunk: BaseChunk) {
        writer.putUInt32(chunk.chunkFourCC)
        writer.putUInt32(chunk.payloadSize)
        reader.reset()
        reader.skip((chunk.offset + 8).toLong())
        reader.read(writer.toByteArray(), writer.position(), chunk.payloadSize)
        writer.skip(chunk.payloadSize)
        if (chunk.payloadSize and 1 == 1) {
            writer.putByte(0.toByte())
        }
    }

    class FrameInfo {
        var bitmap: Bitmap? = null
        var frameX = 0
        var frameY = 0
        var duration = 0
        var blending = false
        var disposal = false
    }

    class FrameBuilder {
        private var frameInfo = FrameInfo()
        fun bitmap(bitmap: Bitmap?): FrameBuilder {
            frameInfo.bitmap = bitmap
            return this
        }

        fun offsetX(x: Int): FrameBuilder {
            frameInfo.frameX = x
            return this
        }

        fun offsetY(y: Int): FrameBuilder {
            frameInfo.frameY = y
            return this
        }

        fun duration(duration: Int): FrameBuilder {
            frameInfo.duration = duration
            return this
        }

        fun blending(blending: Boolean): FrameBuilder {
            frameInfo.blending = blending
            return this
        }

        fun disposal(disposal: Boolean): FrameBuilder {
            frameInfo.disposal = disposal
            return this
        }

        fun build(): FrameInfo {
            return frameInfo
        }
    }

    companion object {
        private val TAG = WebPEncoder::class.java.simpleName

        @Deprecated("")
        fun fromGif(loader: Loader): WebPEncoder {
            val webPEncoder = WebPEncoder()
            webPEncoder.loadGif(loader)
            return webPEncoder
        }

        @JvmStatic
        fun fromDecoder(decoder: FrameSeqDecoder2): WebPEncoder {
            val webPEncoder = WebPEncoder()
            webPEncoder.loadDecoder(decoder)
            return webPEncoder
        }
    }
}
