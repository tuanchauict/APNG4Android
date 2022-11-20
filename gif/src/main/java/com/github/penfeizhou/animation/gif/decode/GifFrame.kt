package com.github.penfeizhou.animation.gif.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Writer
import java.io.IOException

/**
 * @Description: GifFrame
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-16
 */
class GifFrame(
    private val reader: FilterReader,
    globalColorTable: ColorTable?,
    graphicControlExtension: GraphicControlExtension?,
    imageDescriptor: ImageDescriptor
) : Frame() {
    var disposalMethod = 0
    private var transparentColorIndex = 0
    private var colorTable: ColorTable? = null
    private val imageDataOffset: Int
    private val lzwMinCodeSize: Int
    private val interlace: Boolean

    init {
        if (graphicControlExtension != null) {
            disposalMethod = graphicControlExtension.disposalMethod()
            frameDuration =
                (if (graphicControlExtension.delayTime <= 0) DEFAULT_DELAY else graphicControlExtension.delayTime) * 10
            transparentColorIndex = if (graphicControlExtension.transparencyFlag()) {
                graphicControlExtension.transparentColorIndex
            } else {
                -1
            }
        } else {
            disposalMethod = 0
            transparentColorIndex = -1
        }
        frameX = imageDescriptor.frameX
        frameY = imageDescriptor.frameY
        frameWidth = imageDescriptor.frameWidth
        frameHeight = imageDescriptor.frameHeight
        interlace = imageDescriptor.interlaceFlag()
        colorTable = if (imageDescriptor.localColorTableFlag()) {
            imageDescriptor.localColorTable
        } else {
            globalColorTable
        }
        lzwMinCodeSize = imageDescriptor.lzwMinimumCodeSize
        imageDataOffset = imageDescriptor.imageDataOffset
    }

    fun transparencyFlag(): Boolean {
        return transparentColorIndex >= 0
    }

    override fun draw(
        canvas: Canvas,
        paint: Paint,
        sampleSize: Int,
        reusedBitmap: Bitmap,
        writer: Writer
    ): Bitmap {
        try {
            writer.reset(frameWidth * frameHeight / (sampleSize * sampleSize))
            val pixels = writer.asIntArray()
            encode(pixels, sampleSize)
            reusedBitmap.copyPixelsFromBuffer(writer.asIntBuffer().rewind())
            srcRect.left = 0
            srcRect.top = 0
            srcRect.right = reusedBitmap.width
            srcRect.bottom = reusedBitmap.height
            dstRect.left = (frameX.toFloat() / sampleSize).toInt()
            dstRect.top = (frameY.toFloat() / sampleSize).toInt()
            dstRect.right = (frameX.toFloat() / sampleSize + reusedBitmap.width).toInt()
            dstRect.bottom = (frameY.toFloat() / sampleSize + reusedBitmap.height).toInt()
            canvas.drawBitmap(reusedBitmap, srcRect, dstRect, paint)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return reusedBitmap
    }

    @Throws(IOException::class)
    fun encode(pixels: IntArray, sampleSize: Int) {
        reader.reset()
        reader.skip(imageDataOffset.toLong())
        var dataBlock = sDataBlock.get()
        if (dataBlock == null) {
            dataBlock = ByteArray(0xff)
            sDataBlock.set(dataBlock)
        }
        uncompressLZW(
            reader,
            colorTable!!.colorTable,
            transparentColorIndex,
            pixels,
            frameWidth / sampleSize,
            frameHeight / sampleSize,
            lzwMinCodeSize,
            interlace,
            dataBlock
        )
    }

    private external fun uncompressLZW(
        gifReader: FilterReader,
        colorTable: IntArray,
        transparentColorIndex: Int,
        pixels: IntArray,
        width: Int,
        height: Int,
        lzwMinCodeSize: Int,
        interlace: Boolean,
        buffer: ByteArray
    )

    companion object {
        init {
            System.loadLibrary("animation-decoder-gif")
        }

        private val sDataBlock = ThreadLocal<ByteArray>()
        private const val DEFAULT_DELAY = 10
    }
}