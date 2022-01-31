package com.squareup.picasso3

import androidx.annotation.Nullable
import okio.BufferedSource


class ImageDecoderFactory(
    private val decoders: List<ImageDecoder>
) {
    /**
     * Returns the first [ImageDecoder] that can handle the supplied `source`.
     * @param source The source of the image data.
     * @return The first ImageDecoder that can decode the source, or null.
     */
    @Nullable
    fun getImageDecoderForSource(source: BufferedSource?): ImageDecoder? {
        for (decoder in decoders) {
            if (decoder.canHandleSource(source)) {
                return decoder
            }
        }
        return null
    }

}