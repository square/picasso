/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso3

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Build.VERSION
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import okio.*
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.floor

object BitmapUtils {

    internal class ExceptionCatchingSource(delegate: Source?) : ForwardingSource(delegate!!) {
        var thrownException: IOException? = null
        @Throws(IOException::class)
        override fun read(sink: Buffer, byteCount: Long): Long {
            return try {
                super.read(sink, byteCount)
            } catch (e: IOException) {
                thrownException = e
                throw e
            }
        }

        @Throws(IOException::class)
        fun throwIfCaught() {
            if (thrownException is IOException) {
                // TODO: Log when Android returns a non-null Bitmap after swallowing an IOException.
                // TODO: https://github.com/square/picasso/issues/2003/
                throw thrownException as IOException
            }
        }
    }

    /**
     * Lazily create [BitmapFactory.Options] based in given
     * [Request], only instantiating them if needed.
     */
    @JvmStatic
    fun createBitmapOptions(data: Request): BitmapFactory.Options? {
        val justBounds = data.hasSize()
        var options: BitmapFactory.Options? = null
        if (justBounds || data.config != null || data.purgeable) {
            options = BitmapFactory.Options()
            options.inJustDecodeBounds = justBounds
            options.inInputShareable = data.purgeable
            options.inPurgeable = data.purgeable
            if (data.config != null) {
                options.inPreferredConfig = data.config
            }
        }
        return options
    }

    fun requiresInSampleSize(options: BitmapFactory.Options?): Boolean {
        return options != null && options.inJustDecodeBounds
    }

    @JvmStatic
    fun calculateInSampleSize(reqWidth: Int, reqHeight: Int,
                              options: BitmapFactory.Options, request: Request) {
        calculateInSampleSize(reqWidth, reqHeight, options.outWidth, options.outHeight, options,
                request)
    }

    @JvmStatic
    fun shouldResize(onlyScaleDown: Boolean, inWidth: Int, inHeight: Int,
                     targetWidth: Int, targetHeight: Int): Boolean {
        return (!onlyScaleDown || targetWidth != 0 && inWidth > targetWidth
                || targetHeight != 0 && inHeight > targetHeight)
    }

    @JvmStatic
    fun calculateInSampleSize(reqWidth: Int, reqHeight: Int, width: Int, height: Int,
                              options: BitmapFactory.Options, request: Request) {
        var sampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val heightRatio: Int
            val widthRatio: Int
            if (reqHeight == 0) {
                sampleSize = floor(width.toFloat() / reqWidth.toFloat()).toInt()
            } else if (reqWidth == 0) {
                sampleSize = floor(height.toFloat() / reqHeight.toFloat()).toInt()
            } else {
                heightRatio = floor(height.toFloat() / reqHeight.toFloat()).toInt()
                widthRatio = floor(width.toFloat() / reqWidth.toFloat()).toInt()
                sampleSize = if (request.centerInside) Math.max(heightRatio, widthRatio) else Math.min(heightRatio, widthRatio)
            }
        }
        options.inSampleSize = sampleSize
        options.inJustDecodeBounds = false
    }

    /**
     * Decode a byte stream into a Bitmap. This method will take into account additional information
     * about the supplied request in order to do the decoding efficiently (such as through leveraging
     * `inSampleSize`).
     */
    @Throws(IOException::class)
    @JvmStatic
    fun decodeStream(source: Source?, request: Request): Bitmap {
        val exceptionCatchingSource = ExceptionCatchingSource(source)
        val bufferedSource = exceptionCatchingSource.buffer()
        val bitmap = if (VERSION.SDK_INT >= 28) decodeStreamP(request, bufferedSource) else decodeStreamPreP(request, bufferedSource)
        exceptionCatchingSource.throwIfCaught()
        return bitmap
    }

    @RequiresApi(28)
    @SuppressLint("Override")
    @Throws(IOException::class)
    private fun decodeStreamP(request: Request, bufferedSource: BufferedSource): Bitmap {
        val imageSource = ImageDecoder.createSource(ByteBuffer.wrap(bufferedSource.readByteArray()))
        return decodeImageSource(imageSource, request)
    }

    @Throws(IOException::class)
    private fun decodeStreamPreP(request: Request, bufferedSource: BufferedSource): Bitmap {
        val isWebPFile = Utils.isWebPFile(bufferedSource)
        val isPurgeable = request.purgeable && VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        val options = createBitmapOptions(request)
        val calculateSize = requiresInSampleSize(options)
        val bitmap: Bitmap?
        // We decode from a byte array because, a) when decoding a WebP network stream, BitmapFactory
        // throws a JNI Exception, so we workaround by decoding a byte array, or b) user requested
        // purgeable, which only affects bitmaps decoded from byte arrays.
        bitmap = if (isWebPFile || isPurgeable) {
            val bytes = bufferedSource.readByteArray()
            if (calculateSize) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                calculateInSampleSize(request.targetWidth, request.targetHeight,
                        Utils.checkNotNull(options, "options == null"), request)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } else {
            if (calculateSize) {
                BitmapFactory.decodeStream(bufferedSource.peek().inputStream(), null, options)
                calculateInSampleSize(request.targetWidth, request.targetHeight,
                        Utils.checkNotNull(options, "options == null"), request)
            }
            BitmapFactory.decodeStream(bufferedSource.inputStream(), null, options)
        }
        if (bitmap == null) {
            // Treat null as an IO exception, we will eventually retry.
            throw IOException("Failed to decode bitmap.")
        }
        return bitmap
    }

    @Throws(IOException::class)
    fun decodeResource(context: Context, request: Request): Bitmap {
        if (VERSION.SDK_INT >= 28) {
            return decodeResourceP(context, request)
        }
        val resources = Utils.getResources(context, request)
        val id = Utils.getResourceId(resources, request)
        return decodeResourcePreP(resources, id, request)
    }

    @RequiresApi(28)
    @Throws(IOException::class)
    private fun decodeResourceP(context: Context, request: Request): Bitmap {
        val imageSource = ImageDecoder.createSource(context.resources, request.resourceId)
        return decodeImageSource(imageSource, request)
    }

    private fun decodeResourcePreP(resources: Resources, id: Int, request: Request): Bitmap {
        val options = createBitmapOptions(request)
        if (requiresInSampleSize(options)) {
            BitmapFactory.decodeResource(resources, id, options)
            calculateInSampleSize(request.targetWidth, request.targetHeight,
                    Utils.checkNotNull(options, "options == null"), request)
        }
        return BitmapFactory.decodeResource(resources, id, options)
    }

    @RequiresApi(28)
    @Throws(IOException::class)
    private fun decodeImageSource(imageSource: ImageDecoder.Source, request: Request): Bitmap {
        return ImageDecoder.decodeBitmap(imageSource) { imageDecoder, imageInfo, source ->
            if (request.hasSize()) {
                val size = imageInfo.size
                if (shouldResize(request.onlyScaleDown, size.width, size.height,
                                request.targetWidth, request.targetHeight)) {
                    imageDecoder.setTargetSize(request.targetWidth, request.targetHeight)
                }
            }
        }
    }

    fun isXmlResource(resources: Resources, @DrawableRes drawableId: Int): Boolean {
        val typedValue = TypedValue()
        resources.getValue(drawableId, typedValue, true)
        val file = typedValue.string
        return file != null && file.toString().endsWith(".xml")
    }
}