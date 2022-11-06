package com.github.penfeizhou.animation.decode;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.io.Writer;
import com.github.penfeizhou.animation.loader.Loader;

import java.io.IOException;

import kotlin.jvm.functions.Function1;

public abstract class FrameSeqDecoder<R extends Reader, W extends Writer> extends FrameSeqDecoder2<R, W> {
    private static final String TAG = FrameSeqDecoder.class.getSimpleName();

    public static final boolean DEBUG = false;

    /**
     * @param loader         webp-like reader
     * @param renderListener Callbacks for rendering
     */
    public FrameSeqDecoder(
            Loader loader,
            @Nullable RenderListener renderListener,
            Function1<Reader, R> readerFactory
    ) {
        super(loader, renderListener, readerFactory);
    }

    private String debugInfo() {
        if (DEBUG) {
            return String.format("thread is %s, decoder is %s,state is %s", Thread.currentThread(), FrameSeqDecoder.this, getState());
        }
        return "";
    }

    /**
     * Get Indexed frame
     *
     * @param index <0 means reverse from last index
     */
    public Bitmap getFrameBitmap(int index) throws IOException {
        if (getState() != State.IDLE) {
            Log.e(TAG, debugInfo() + ",stop first");
            return null;
        }
        setState(State.RUNNING);
        paused.compareAndSet(true, false);
        if (frames.size() == 0) {
            initCanvasBounds();
        }
        if (index < 0) {
            index += this.frames.size();
        }
        if (index < 0) {
            index = 0;
        }
        frameIndex = -1;
        while (frameIndex < index) {
            if (canStep()) {
                step();
            } else {
                break;
            }
        }
        if (frameBuffer != null) {
            frameBuffer.rewind();
        }
        Bitmap bitmap = Bitmap.createBitmap(getBounds().width() / sampleSize, getBounds().height() / sampleSize, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(frameBuffer);
        innerStop();
        return bitmap;
    }
}
