package com.github.penfeizhou.animation.decode;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.io.Writer;
import com.github.penfeizhou.animation.loader.Loader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.LockSupport;

import kotlin.Unit;

public abstract class FrameSeqDecoder<R extends Reader, W extends Writer> extends BaseFrameSeqDecoder<R, W> {
    private static final String TAG = FrameSeqDecoder.class.getSimpleName();

    private final Loader mLoader;

    private int playCount;
    private Integer loopLimit = null;
    private static final Rect RECT_EMPTY = new Rect();

    protected int sampleSize = 1;

    protected Map<Bitmap, Canvas> cachedCanvas = new WeakHashMap<>();

    protected volatile Rect fullRect;
    private W mWriter = getWriter();
    private R mReader = null;
    public static final boolean DEBUG = false;
    /**
     * If played all the needed
     */
    private boolean finished = false;

    private enum State {
        IDLE,
        RUNNING,
        INITIALIZING,
        FINISHING,
    }

    private volatile State mState = State.IDLE;

    protected abstract W getWriter();

    protected abstract R getReader(Reader reader);

    /**
     * Rendering callbacks for decoders
     */
    public interface RenderListener {
        /**
         * Playback starts
         */
        void onStart();

        /**
         * Frame Playback
         */
        void onRender(@NonNull ByteBuffer byteBuffer);

        /**
         * End of Playback
         */
        void onEnd();
    }


    /**
     * @param loader         webp-like reader
     * @param renderListener Callbacks for rendering
     */
    public FrameSeqDecoder(Loader loader, @Nullable RenderListener renderListener) {
        super(renderListener);
        this.mLoader = loader;
    }

    public void stopIfNeeded() {
        this.workerHandler.post(() -> {
            if (renderListeners.size() == 0) {
                stop();
            }
        });
    }

    public Rect getBounds() {
        if (fullRect == null) {
            if (mState == State.FINISHING) {
                Log.e(TAG, "In finishing,do not interrupt");
            }
            final Thread thread = Thread.currentThread();
            workerHandler.post(() -> {
                try {
                    if (fullRect == null) {
                        if (mReader == null) {
                            mReader = getReader(mLoader.obtain());
                        } else {
                            mReader.reset();
                        }
                        initCanvasBounds(read(mReader));
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

    private void initCanvasBounds(Rect rect) {
        fullRect = rect;
        frameBuffer = ByteBuffer.allocate((rect.width() * rect.height() / (sampleSize * sampleSize) + 1) * 4);
        if (mWriter == null) {
            mWriter = getWriter();
        }
    }


    public int getFrameCount() {
        return this.frames.size();
    }

    /**
     * @return Loop Count defined in file
     */
    protected abstract int getLoopCount();

    public void start() {
        if (fullRect == RECT_EMPTY) {
            return;
        }
        if (mState == State.RUNNING || mState == State.INITIALIZING) {
            Log.i(TAG, debugInfo() + " Already started");
            return;
        }
        if (mState == State.FINISHING) {
            Log.e(TAG, debugInfo() + " Processing,wait for finish at " + mState);
        }
        if (DEBUG) {
            Log.i(TAG, debugInfo() + "Set state to INITIALIZING");
        }
        mState = State.INITIALIZING;

        ensureWorkerExecute(() -> {
            innerStart();
            return Unit.INSTANCE;
        });
    }

    @WorkerThread
    private void innerStart() {
        paused.compareAndSet(true, false);

        final long start = System.currentTimeMillis();
        try {
            if (frames.size() == 0) {
                try {
                    if (mReader == null) {
                        mReader = getReader(mLoader.obtain());
                    } else {
                        mReader.reset();
                    }
                    initCanvasBounds(read(mReader));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        } finally {
            Log.i(TAG, debugInfo() + " Set state to RUNNING,cost " + (System.currentTimeMillis() - start));
            mState = State.RUNNING;
        }
        if (getNumPlays() == 0 || !finished) {
            this.frameIndex = -1;
            renderTask.run();
            for (RenderListener renderListener : renderListeners) {
                renderListener.onStart();
            }
        } else {
            Log.i(TAG, debugInfo() + " No need to started");
        }
    }

    @WorkerThread
    private void innerStop() {
        workerHandler.removeCallbacks(renderTask);
        frames.clear();
        clearBitmapPool();

        if (frameBuffer != null) {
            frameBuffer = null;
        }
        cachedCanvas.clear();
        try {
            if (mReader != null) {
                mReader.close();
                mReader = null;
            }
            if (mWriter != null) {
                mWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        release();
        if (DEBUG) {
            Log.i(TAG, debugInfo() + " release and Set state to IDLE");
        }
        mState = State.IDLE;
        for (RenderListener renderListener : renderListeners) {
            renderListener.onEnd();
        }
    }


    @Override
    public void stop() {
        if (fullRect == RECT_EMPTY) {
            return;
        }
        if (mState == State.FINISHING || mState == State.IDLE) {
            Log.i(TAG, debugInfo() + "No need to stop");
            return;
        }
        if (mState == State.INITIALIZING) {
            Log.e(TAG, debugInfo() + "Processing,wait for finish at " + mState);
        }
        if (DEBUG) {
            Log.i(TAG, debugInfo() + " Set state to finishing");
        }
        mState = State.FINISHING;

        ensureWorkerExecute(() -> {
            innerStop();
            return Unit.INSTANCE;
        });
    }

    private String debugInfo() {
        if (DEBUG) {
            return String.format("thread is %s, decoder is %s,state is %s", Thread.currentThread(), FrameSeqDecoder.this, mState.toString());
        }
        return "";
    }

    protected abstract void release();

    public boolean isRunning() {
        return mState == State.RUNNING || mState == State.INITIALIZING;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void setLoopLimit(int limit) {
        this.loopLimit = limit;
    }

    public void reset() {
        ensureWorkerExecute(() -> {
            playCount = 0;
            frameIndex = -1;
            finished = false;
            return Unit.INSTANCE;
        });
    }

    public void pause() {
        workerHandler.removeCallbacks(renderTask);
        paused.compareAndSet(false, true);
    }

    public void resume() {
        paused.compareAndSet(true, false);
        workerHandler.removeCallbacks(renderTask);
        workerHandler.post(renderTask);
    }


    public int getSampleSize() {
        return sampleSize;
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
                    initCanvasBounds(read(getReader(mLoader.obtain())));
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

    protected abstract Rect read(R reader) throws IOException;

    private int getNumPlays() {
        return this.loopLimit != null ? this.loopLimit : this.getLoopCount();
    }

    @Override
    protected boolean canStep() {
        if (!isRunning()) {
            return false;
        }
        if (frames.size() == 0) {
            return false;
        }
        if (getNumPlays() <= 0) {
            return true;
        }
        if (this.playCount < getNumPlays() - 1) {
            return true;
        } else if (this.playCount == getNumPlays() - 1 && this.frameIndex < this.getFrameCount() - 1) {
            return true;
        }
        finished = true;
        return false;
    }

    @WorkerThread
    protected long step() {
        this.frameIndex++;
        if (this.frameIndex >= this.getFrameCount()) {
            this.frameIndex = 0;
            this.playCount++;
        }
        Frame<R, W> frame = getFrame(this.frameIndex);
        if (frame == null) {
            return 0;
        }
        renderFrame(frame);
        return frame.frameDuration;
    }

    protected abstract void renderFrame(Frame<R, W> frame);

    public Frame<R, W> getFrame(int index) {
        if (index < 0 || index >= frames.size()) {
            return null;
        }
        return frames.get(index);
    }

    /**
     * Get Indexed frame
     *
     * @param index <0 means reverse from last index
     */
    public Bitmap getFrameBitmap(int index) throws IOException {
        if (mState != State.IDLE) {
            Log.e(TAG, debugInfo() + ",stop first");
            return null;
        }
        mState = State.RUNNING;
        paused.compareAndSet(true, false);
        if (frames.size() == 0) {
            if (mReader == null) {
                mReader = getReader(mLoader.obtain());
            } else {
                mReader.reset();
            }
            initCanvasBounds(read(mReader));
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
        Bitmap bitmap = Bitmap.createBitmap(getBounds().width() / getSampleSize(), getBounds().height() / getSampleSize(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(frameBuffer);
        innerStop();
        return bitmap;
    }
}
