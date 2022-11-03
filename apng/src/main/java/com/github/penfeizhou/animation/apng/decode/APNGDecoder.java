package com.github.penfeizhou.animation.apng.decode;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;

import com.github.penfeizhou.animation.apng.io.APNGReader;
import com.github.penfeizhou.animation.apng.io.APNGWriter;
import com.github.penfeizhou.animation.decode.Frame;
import com.github.penfeizhou.animation.decode.FrameSeqDecoder;
import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.loader.Loader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
public class APNGDecoder extends FrameSeqDecoder<APNGReader, APNGWriter> {

    private APNGWriter apngWriter;
    private int mLoopCount;
    private final Paint paint = new Paint();


    private static class SnapShot {
        byte dispose_op;
        Rect dstRect = new Rect();
        ByteBuffer byteBuffer;
    }

    private final SnapShot snapShot = new SnapShot();

    /**
     * @param loader         webp的reader
     * @param renderListener 渲染的回调
     */
    public APNGDecoder(Loader loader, RenderListener renderListener) {
        super(loader, renderListener);
        paint.setAntiAlias(true);
    }

    @Override
    protected APNGWriter getWriter() {
        if (apngWriter == null) {
            apngWriter = new APNGWriter();
        }
        return apngWriter;
    }

    @Override
    protected APNGReader getReader(Reader reader) {
        return new APNGReader(reader);
    }

    @Override
    protected int getLoopCount() {
        return mLoopCount;
    }

    @Override
    protected void release() {
        snapShot.byteBuffer = null;
        apngWriter = null;
    }


    @Override
    protected Rect read(APNGReader reader) throws IOException {
        List<Chunk> chunks = APNGParser.parse(reader);
        List<Chunk> otherChunks = new ArrayList<>();

        boolean actl = false;
        APNGFrame lastFrame = null;
        byte[] ihdrData = new byte[0];
        int canvasWidth = 0, canvasHeight = 0;
        for (Chunk chunk : chunks) {
            if (chunk instanceof ACTLChunk) {
                mLoopCount = ((ACTLChunk) chunk).num_plays;
                actl = true;
            } else if (chunk instanceof FCTLChunk) {
                APNGFrame frame = new APNGFrame(reader, (FCTLChunk) chunk);
                frame.prefixChunks = otherChunks;
                frame.ihdrData = ihdrData;
                frames.add(frame);
                lastFrame = frame;
            } else if (chunk instanceof FDATChunk) {
                if (lastFrame != null) {
                    lastFrame.imageChunks.add(chunk);
                }
            } else if (chunk instanceof IDATChunk) {
                if (!actl) {
                    // If it is a non-APNG image, only PNG will be decoded
                    Frame<APNGReader, APNGWriter> frame = new StillFrame(reader);
                    frame.frameWidth = canvasWidth;
                    frame.frameHeight = canvasHeight;
                    frames.add(frame);
                    mLoopCount = 1;
                    break;
                }
                if (lastFrame != null) {
                    lastFrame.imageChunks.add(chunk);
                }

            } else if (chunk instanceof IHDRChunk) {
                canvasWidth = ((IHDRChunk) chunk).width;
                canvasHeight = ((IHDRChunk) chunk).height;
                ihdrData = ((IHDRChunk) chunk).data;
            } else if (!(chunk instanceof IENDChunk)) {
                otherChunks.add(chunk);
            }
        }
        frameBuffer = ByteBuffer.allocate((canvasWidth * canvasHeight / (sampleSize * sampleSize) + 1) * 4);
        snapShot.byteBuffer = ByteBuffer.allocate((canvasWidth * canvasHeight / (sampleSize * sampleSize) + 1) * 4);
        return new Rect(0, 0, canvasWidth, canvasHeight);
    }

    @Override
    protected void renderFrame(Frame<APNGReader, APNGWriter> frame) {
        if (frame == null || fullRect == null) {
            return;
        }
        try {
            Bitmap bitmap = obtainBitmap(fullRect.width() / sampleSize, fullRect.height() / sampleSize);
            Canvas canvas = cachedCanvas.get(bitmap);
            if (canvas == null) {
                canvas = new Canvas(bitmap);
                cachedCanvas.put(bitmap, canvas);
            }
            if (frame instanceof APNGFrame) {
                // Restore the current frame from the cache
                frameBuffer.rewind();
                bitmap.copyPixelsFromBuffer(frameBuffer);
                // Process the settings in the snapshot before starting to draw
                if (this.frameIndex == 0) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                } else {
                    canvas.save();
                    canvas.clipRect(snapShot.dstRect);
                    switch (snapShot.dispose_op) {
                        // Restore the display from the snapshot before the last frame
                        case FCTLChunk.APNG_DISPOSE_OP_PREVIOUS:
                            snapShot.byteBuffer.rewind();
                            bitmap.copyPixelsFromBuffer(snapShot.byteBuffer);
                            break;
                        // Clear the area drawn in the previous frame
                        case FCTLChunk.APNG_DISPOSE_OP_BACKGROUND:
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                            break;
                        // Do nothing
                        case FCTLChunk.APNG_DISPOSE_OP_NON:
                        default:
                            break;
                    }
                    canvas.restore();
                }

                // Then pass it to the snapshot information according to the dispose setting
                if (((APNGFrame) frame).dispose_op == FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
                    if (snapShot.dispose_op != FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
                        snapShot.byteBuffer.rewind();
                        bitmap.copyPixelsToBuffer(snapShot.byteBuffer);
                    }
                }

                snapShot.dispose_op = ((APNGFrame) frame).dispose_op;
                canvas.save();
                if (((APNGFrame) frame).blend_op == FCTLChunk.APNG_BLEND_OP_SOURCE) {
                    canvas.clipRect(
                            frame.frameX / sampleSize,
                            frame.frameY / sampleSize,
                            (frame.frameX + frame.frameWidth) / sampleSize,
                            (frame.frameY + frame.frameHeight) / sampleSize);
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                }


                snapShot.dstRect.set(frame.frameX / sampleSize,
                        frame.frameY / sampleSize,
                        (frame.frameX + frame.frameWidth) / sampleSize,
                        (frame.frameY + frame.frameHeight) / sampleSize);
                canvas.restore();
            }
            // Start actually drawing the content of the current frame
            Bitmap inBitmap = obtainBitmap(frame.frameWidth, frame.frameHeight);
            recycleBitmap(frame.draw(canvas, paint, sampleSize, inBitmap, getWriter()));
            recycleBitmap(inBitmap);
            frameBuffer.rewind();
            bitmap.copyPixelsToBuffer(frameBuffer);
            recycleBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
