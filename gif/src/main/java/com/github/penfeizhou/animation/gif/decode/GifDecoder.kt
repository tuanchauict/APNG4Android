package com.github.penfeizhou.animation.gif.decode

import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.Size
import com.github.penfeizhou.animation.decode.FrameSeqDecoder2
import com.github.penfeizhou.animation.decode.ImageInfo
import com.github.penfeizhou.animation.decode.KFrame
import com.github.penfeizhou.animation.gif.io.GifWriter
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.loader.Loader
import java.io.IOException
import java.nio.ByteBuffer

/**
 * @Description: GifDecoder
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-16
 */
class GifDecoder(loader: Loader) : FrameSeqDecoder2(loader) {
    private val paint = Paint().apply { isAntiAlias = true }
    private var bgColor = Color.TRANSPARENT
    private val snapShot = SnapShot()

    private class SnapShot {
        var byteBuffer: ByteBuffer? = null
    }

    private val writer: GifWriter by lazy { GifWriter() }

    override fun release() {
        snapShot.byteBuffer = null
    }

    @Throws(IOException::class)
    override fun read(reader: FilterReader): ImageInfo {
        val blocks = GifParser.parse(reader)
        var canvasWidth = 0
        var canvasHeight = 0
        var globalColorTable: ColorTable? = null
        var graphicControlExtension: GraphicControlExtension? = null
        var bgColorIndex = -1
        var loopCount = 0
        val frames = mutableListOf<KFrame>()

        for (block in blocks) {
            if (block is LogicalScreenDescriptor) {
                canvasWidth = block.screenWidth
                canvasHeight = block.screenHeight
                if (block.gColorTableFlag()) {
                    bgColorIndex = block.bgColorIndex.toInt() and 0xff
                }
            } else if (block is ColorTable) {
                globalColorTable = block
            } else if (block is GraphicControlExtension) {
                graphicControlExtension = block
            } else if (block is ImageDescriptor) {
                val gifFrame = GifFrame(reader, globalColorTable, graphicControlExtension, block)
                frames.add(gifFrame)
            } else if (block is ApplicationExtension && "NETSCAPE2.0" == block.identifier) {
                loopCount = block.loopCount
            }
        }
        snapShot.byteBuffer =
            ByteBuffer.allocate((canvasWidth * canvasHeight / (sampleSize * sampleSize) + 1) * 4)
        if (globalColorTable != null && bgColorIndex >= 0 && bgColorIndex < globalColorTable.colorTable.size) {
            val abgr = globalColorTable.colorTable[bgColorIndex]
            bgColor = Color.rgb(abgr and 0xff, abgr shr 8 and 0xff, abgr shr 16 and 0xff)
        }
        return ImageInfo(loopCount, Size(canvasWidth, canvasHeight), frames)
    }

    override fun getDesiredSample(desiredWidth: Int, desiredHeight: Int): Int = 1

    override fun renderFrame(imageInfo: ImageInfo, frame: KFrame, frameBuffer: ByteBuffer) {
        val gifFrame = frame as GifFrame
        val bitmap = obtainBitmap(
            imageInfo.viewport.width / sampleSize,
            imageInfo.viewport.height / sampleSize
        ) ?: return
        val canvas = getCanvas(bitmap)

        frameBuffer.rewind()
        bitmap.copyPixelsFromBuffer(frameBuffer)
        var backgroundColor = Color.TRANSPARENT
        if (!gifFrame.transparencyFlag()) {
            backgroundColor = bgColor
        }
        if (frameIndex == 0) {
            bitmap.eraseColor(backgroundColor)
        } else {
            val preFrame = imageInfo.frames[frameIndex - 1] as GifFrame
            canvas.save()
            canvas.clipRect(
                preFrame.frameX / sampleSize,
                preFrame.frameY / sampleSize,
                (preFrame.frameX + preFrame.frameWidth) / sampleSize,
                (preFrame.frameY + preFrame.frameHeight) / sampleSize
            )
            when (preFrame.disposalMethod) {
                0 -> {}
                1 -> {}
                2 -> canvas.drawColor(bgColor, PorterDuff.Mode.CLEAR)
                3 -> {
                    snapShot.byteBuffer!!.rewind()
                    canvas.drawColor(bgColor, PorterDuff.Mode.CLEAR)
                    val preBitmap = obtainBitmap(
                        imageInfo.viewport.width / sampleSize,
                        imageInfo.viewport.height / sampleSize
                    )
                    preBitmap!!.copyPixelsFromBuffer(snapShot.byteBuffer)
                    canvas.drawBitmap(preBitmap, 0f, 0f, paint)
                    recycleBitmap(preBitmap)
                }
            }
            canvas.restore()
            if (gifFrame.disposalMethod == 3) {
                if (preFrame.disposalMethod != 3) {
                    frameBuffer.rewind()
                    snapShot.byteBuffer!!.rewind()
                    snapShot.byteBuffer!!.put(frameBuffer)
                }
            }
        }
        val reused =
            obtainBitmap(frame.frameWidth / sampleSize, frame.frameHeight / sampleSize)
                ?: return
        gifFrame.draw(canvas, paint, sampleSize, reused, writer)
        canvas.drawColor(backgroundColor, PorterDuff.Mode.DST_OVER)
        recycleBitmap(reused)
        frameBuffer.rewind()
        bitmap.copyPixelsToBuffer(frameBuffer)
        recycleBitmap(bitmap)
    }
}
