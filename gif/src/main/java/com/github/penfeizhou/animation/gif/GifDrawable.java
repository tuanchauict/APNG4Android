package com.github.penfeizhou.animation.gif;

import android.content.Context;

import com.github.penfeizhou.animation.FrameAnimationDrawable;
import com.github.penfeizhou.animation.decode.RenderListener;
import com.github.penfeizhou.animation.gif.decode.GifDecoder;
import com.github.penfeizhou.animation.loader.AssetStreamLoader;
import com.github.penfeizhou.animation.loader.FileLoader;
import com.github.penfeizhou.animation.loader.Loader;
import com.github.penfeizhou.animation.loader.ResourceStreamLoader;

/**
 * @Description: GifDrawable
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-16
 */
public class GifDrawable extends FrameAnimationDrawable {
    public GifDrawable(Loader provider) {
        super(new GifDecoder(provider));
    }

    public GifDrawable(GifDecoder decoder) {
        super(decoder);
    }

    public static GifDrawable fromAsset(Context context, String assetPath) {
        AssetStreamLoader assetStreamLoader = new AssetStreamLoader(context, assetPath);
        return new GifDrawable(assetStreamLoader);
    }

    public static GifDrawable fromFile(String filePath) {
        FileLoader fileLoader = new FileLoader(filePath);
        return new GifDrawable(fileLoader);
    }

    public static GifDrawable fromResource(Context context, int resId) {
        ResourceStreamLoader resourceStreamLoader = new ResourceStreamLoader(context, resId);
        return new GifDrawable(resourceStreamLoader);
    }
}
