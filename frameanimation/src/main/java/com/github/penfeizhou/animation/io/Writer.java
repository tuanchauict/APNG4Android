package com.github.penfeizhou.animation.io;

import java.io.IOException;
import java.nio.IntBuffer;

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-12
 */
public interface Writer {
    void reset(int size);

    void putByte(byte b);

    void putBytes(byte[] b);

    int position();

    void skip(int length);

    byte[] toByteArray();

    void close() throws IOException;

    default int[] asIntArray() {
        throw new UnsupportedOperationException();
    }

    default IntBuffer asIntBuffer() {
        throw new UnsupportedOperationException();
    }
}
