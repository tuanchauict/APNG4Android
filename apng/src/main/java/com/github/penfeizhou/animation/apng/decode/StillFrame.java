package com.github.penfeizhou.animation.apng.decode;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import com.github.penfeizhou.animation.apng.io.APNGReader;
import com.github.penfeizhou.animation.decode.Frame;
import com.github.penfeizhou.animation.io.Writer;

import java.io.IOException;

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
public class StillFrame extends Frame<Writer> {
    @NonNull
    private final APNGReader reader;

    public StillFrame(@NonNull APNGReader reader) {
        this.reader = reader;
    }

    @Override
    public Bitmap draw(Canvas canvas, Paint paint, int sampleSize, Bitmap reusedBitmap, Writer writer) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        options.inMutable = true;
        options.inBitmap = reusedBitmap;
        Bitmap bitmap = null;
        try {
            reader.reset();

            try {
                bitmap = BitmapFactory.decodeStream(reader.toInputStream(), null, options);
            } catch (IllegalArgumentException e) {
                // Problem decoding into existing bitmap when on Android 4.2.2 & 4.3
                BitmapFactory.Options optionsFixed = new BitmapFactory.Options();
                optionsFixed.inJustDecodeBounds = false;
                optionsFixed.inSampleSize = sampleSize;
                optionsFixed.inMutable = true;
                bitmap = BitmapFactory.decodeStream(reader.toInputStream(), null, optionsFixed);
            }
            assert bitmap != null;
            paint.setXfermode(null);
            canvas.drawBitmap(bitmap, 0, 0, paint);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
}
