package com.github.penfeizhou.animation.apng

import android.content.Context
import com.github.penfeizhou.animation.FrameAnimationDrawable
import com.github.penfeizhou.animation.apng.decode.APNGDecoder
import com.github.penfeizhou.animation.loader.AssetStreamLoader
import com.github.penfeizhou.animation.loader.FileLoader
import com.github.penfeizhou.animation.loader.Loader
import com.github.penfeizhou.animation.loader.ResourceStreamLoader

/**
 * @Description: APNGDrawable
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
class APNGDrawable(decoder: APNGDecoder) : FrameAnimationDrawable(decoder) {
    constructor(streamLoader: Loader) : this(APNGDecoder(streamLoader))

    companion object {
        @JvmStatic
        fun fromAsset(context: Context?, assetPath: String?): APNGDrawable {
            val assetStreamLoader = AssetStreamLoader(context, assetPath)
            return APNGDrawable(assetStreamLoader)
        }

        fun fromFile(filePath: String?): APNGDrawable {
            val fileLoader = FileLoader(filePath)
            return APNGDrawable(fileLoader)
        }

        fun fromResource(context: Context?, resId: Int): APNGDrawable {
            val resourceStreamLoader = ResourceStreamLoader(context, resId)
            return APNGDrawable(resourceStreamLoader)
        }
    }
}
