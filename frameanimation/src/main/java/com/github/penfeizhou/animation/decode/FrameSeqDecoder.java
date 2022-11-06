package com.github.penfeizhou.animation.decode;

import androidx.annotation.Nullable;

import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.io.Writer;
import com.github.penfeizhou.animation.loader.Loader;

import kotlin.jvm.functions.Function1;

public abstract class FrameSeqDecoder<R extends Reader, W extends Writer> extends FrameSeqDecoder2<R, W> {
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
}
