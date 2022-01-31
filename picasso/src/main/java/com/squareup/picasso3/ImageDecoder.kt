package com.squareup.picasso3

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import okio.BufferedSource
import java.io.IOException


interface ImageDecoder {
    class Image @JvmOverloads constructor(
        @field:Nullable @param:Nullable val bitmap: Bitmap?,
        @field:Nullable @param:Nullable val drawable: Drawable? = null,
        val exifOrientation: Int = 0
    ) {
        constructor(@NonNull drawable: Drawable?) : this(null, drawable, 0)
    }

    fun canHandleSource(source: BufferedSource?): Boolean

    @Throws(IOException::class)
    fun decodeImage(source: BufferedSource?, request: Request?): Image?
}