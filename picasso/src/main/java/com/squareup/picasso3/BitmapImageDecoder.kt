package com.squareup.picasso3

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.squareup.picasso3.BitmapUtils.calculateInSampleSize
import com.squareup.picasso3.BitmapUtils.createBitmapOptions
import com.squareup.picasso3.BitmapUtils.requiresInSampleSize

import okio.BufferedSource
import java.io.IOException
import java.io.InputStream


class BitmapImageDecoder : ImageDecoder {
    override fun canHandleSource(source: BufferedSource?): Boolean {
        val source = source!!.peek()
        return try {
            if (Utils.isWebPFile(source)) {
                return true
            }
            val stream: InputStream = source.inputStream()
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(stream, null, options)
            // we successfully decoded the bounds
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Decode a byte stream into a Bitmap. This method will take into account additional information
     * about the supplied request in order to do the decoding efficiently (such as through leveraging
     * `inSampleSize`).
     */
    @Throws(IOException::class)
    override fun decodeImage(source: BufferedSource?, request: Request?): ImageDecoder.Image? {
        val isWebPFile = Utils.isWebPFile(source!!.peek())
        val isPurgeable =
            request!!.purgeable && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        val options: BitmapFactory.Options = createBitmapOptions(request)!!
        val calculateSize: Boolean = requiresInSampleSize(options)
        val bitmap: Bitmap?
        // We decode from a byte array because, a) when decoding a WebP network stream, BitmapFactory
        // throws a JNI Exception, so we workaround by decoding a byte array, or b) user requested
        // purgeable, which only affects bitmaps decoded from byte arrays.
        bitmap = if (isWebPFile || isPurgeable) {
            val bytes = source.peek().readByteArray()
            if (calculateSize) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                calculateInSampleSize(
                    request.targetWidth, request.targetHeight, options,
                    request
                )
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } else {
            if (calculateSize) {
                val stream: InputStream = source.peek().inputStream()
                BitmapFactory.decodeStream(stream, null, options)
                calculateInSampleSize(
                    request.targetWidth, request.targetHeight, options,
                    request
                )
            }
            BitmapFactory.decodeStream(source.peek().inputStream(), null, options)
        }
        if (bitmap == null) {
            // Treat null as an IO exception, we will eventually retry.
            throw IOException("Failed to decode bitmap.")
        }
        return ImageDecoder.Image(bitmap)
    }
}