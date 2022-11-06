package com.github.penfeizhou.animation.decode;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.Nullable;

import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.io.Writer;
import com.github.penfeizhou.animation.loader.Loader;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public abstract class FrameSeqDecoder<R extends Reader, W extends Writer> extends BaseFrameSeqDecoder<R, W> {
    private static final String TAG = FrameSeqDecoder.class.getSimpleName();

    private static final Rect RECT_EMPTY = new Rect();

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

    public Rect getBounds() {
        if (fullRect == null) {
            if (getState() == State.FINISHING) {
                Log.e(TAG, "In finishing,do not interrupt");
            }
            final Thread thread = Thread.currentThread();
            workerHandler.post(() -> {
                try {
                    if (fullRect == null) {
                        initCanvasBounds(read());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    fullRect = RECT_EMPTY;
                } finally {
                    LockSupport.unpark(thread);
                }
            });
            LockSupport.park(thread);
        }
        return fullRect == null ? RECT_EMPTY : fullRect;
    }

    private String debugInfo() {
        if (DEBUG) {
            return String.format("thread is %s, decoder is %s,state is %s", Thread.currentThread(), FrameSeqDecoder.this, getState());
        }
        return "";
    }

    public boolean setDesiredSize(int width, int height) {
        boolean sampleSizeChanged = false;
        final int sample = getDesiredSample(width, height);
        if (sample != this.sampleSize) {
            sampleSizeChanged = true;
            final boolean tempRunning = isRunning();
            workerHandler.removeCallbacks(renderTask);

            ensureWorkerExecute(() -> {
                innerStop();
                try {
                    sampleSize = sample;
                    initCanvasBounds(read());
                    if (tempRunning) {
                        innerStart();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return Unit.INSTANCE;
            });
        }
        return sampleSizeChanged;
    }

    protected int getDesiredSample(int desiredWidth, int desiredHeight) {
        if (desiredWidth == 0 || desiredHeight == 0) {
            return 1;
        }
        int radio = Math.min(getBounds().width() / desiredWidth, getBounds().height() / desiredHeight);
        int sample = 1;
        while ((sample * 2) <= radio) {
            sample *= 2;
        }
        return sample;
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
            initCanvasBounds(read());
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
