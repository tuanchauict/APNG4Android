package com.github.penfeizhou.animation.decode

import java.nio.ByteBuffer

/**
 * Rendering callbacks for decoders
 */
interface RenderListener {
    /**
     * Playback starts
     */
    fun onStart()

    /**
     * Frame Playback
     */
    fun onRender(byteBuffer: ByteBuffer)

    /**
     * End of Playback
     */
    fun onEnd()
}
