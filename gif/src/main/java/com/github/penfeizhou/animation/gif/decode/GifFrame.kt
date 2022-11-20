package com.github.penfeizhou.animation.gif.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.github.penfeizhou.animation.decode.KFrame
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
) : KFrame(
    x = imageDescriptor.frameX,
    y = imageDescriptor.frameY,
    width = imageDescriptor.frameWidth,
    height = imageDescriptor.frameHeight,
    duration = graphicControlExtension?.getDuration() ?: 0
) {
    val disposalMethod: Int = graphicControlExtension?.disposalMethod() ?: 0
    private val transparentColorIndex: Int =
        graphicControlExtension?.getTransparentColorIndex() ?: -1
    private var colorTable: ColorTable? =
        if (imageDescriptor.localColorTableFlag()) {
            imageDescriptor.localColorTable
        } else {
            globalColorTable
        }
    private val imageDataOffset: Int = imageDescriptor.imageDataOffset
    private val lzwMinCodeSize: Int = imageDescriptor.lzwMinimumCodeSize
    private val interlace: Boolean = imageDescriptor.interlaceFlag()

    fun transparencyFlag(): Boolean = transparentColorIndex >= 0

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

        private fun GraphicControlExtension.getDuration(): Int =
            (if (delayTime <= 0) DEFAULT_DELAY else delayTime) * 10

        private fun GraphicControlExtension.getTransparentColorIndex(): Int =
            if (transparencyFlag()) transparentColorIndex else -1
    }
}
