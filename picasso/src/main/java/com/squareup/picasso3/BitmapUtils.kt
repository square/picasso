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
import android.os.Build.VERSION
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

internal object BitmapUtils {
  /**
   * Lazily create [BitmapFactory.Options] based in given
   * [Request], only instantiating them if needed.
   */
  fun createBitmapOptions(data: Request): BitmapFactory.Options? {
    val justBounds = data.hasSize()
    return if (justBounds || data.config != null) {
      BitmapFactory.Options().apply {
        inJustDecodeBounds = justBounds
        if (data.config != null) {
          inPreferredConfig = data.config
        }
      }
    } else null
  }

  fun requiresInSampleSize(options: BitmapFactory.Options?): Boolean {
    return options != null && options.inJustDecodeBounds
  }

  fun calculateInSampleSize(
    reqWidth: Int,
    reqHeight: Int,
    options: BitmapFactory.Options,
    request: Request
  ) {
    calculateInSampleSize(
      reqWidth, reqHeight, options.outWidth, options.outHeight, options, request
    )
  }

  fun shouldResize(
    onlyScaleDown: Boolean,
    inWidth: Int,
    inHeight: Int,
    targetWidth: Int,
    targetHeight: Int
  ): Boolean {
    return (
      !onlyScaleDown || targetWidth != 0 && inWidth > targetWidth ||
        targetHeight != 0 && inHeight > targetHeight
      )
  }

  fun calculateInSampleSize(
    requestWidth: Int,
    requestHeight: Int,
    width: Int,
    height: Int,
    options: BitmapFactory.Options,
    request: Request
  ) {
    options.inSampleSize = ratio(requestWidth, requestHeight, width, height, request)
    options.inJustDecodeBounds = false
  }

  /**
   * Decode a byte stream into a Bitmap. This method will take into account additional information
   * about the supplied request in order to do the decoding efficiently (such as through leveraging
   * `inSampleSize`).
   */
  fun decodeStream(source: Source, request: Request): Bitmap {
    val exceptionCatchingSource = ExceptionCatchingSource(source)
    val bufferedSource = exceptionCatchingSource.buffer()
    val bitmap =
      if (VERSION.SDK_INT >= 28)
        decodeStreamP(request, bufferedSource)
      else
        decodeStreamPreP(request, bufferedSource)
    exceptionCatchingSource.throwIfCaught()
    return bitmap
  }

  @RequiresApi(28)
  @SuppressLint("Override")
  private fun decodeStreamP(request: Request, bufferedSource: BufferedSource): Bitmap {
    val imageSource = ImageDecoder.createSource(ByteBuffer.wrap(bufferedSource.readByteArray()))
    return decodeImageSource(imageSource, request)
  }

  private fun decodeStreamPreP(request: Request, bufferedSource: BufferedSource): Bitmap {
    val isWebPFile = Utils.isWebPFile(bufferedSource)
    val options = createBitmapOptions(request)
    val calculateSize = requiresInSampleSize(options)
    // We decode from a byte array because, when decoding a WebP network stream, BitmapFactory
    // throws a JNI Exception, so we workaround by decoding a byte array.
    val bitmap = if (isWebPFile) {
      val bytes = bufferedSource.readByteArray()
      if (calculateSize) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        calculateInSampleSize(request.targetWidth, request.targetHeight, options!!, request)
      }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } else {
      if (calculateSize) {
        BitmapFactory.decodeStream(bufferedSource.peek().inputStream(), null, options)
        calculateInSampleSize(request.targetWidth, request.targetHeight, options!!, request)
      }
      BitmapFactory.decodeStream(bufferedSource.inputStream(), null, options)
    }
    if (bitmap == null) {
      // Treat null as an IO exception, we will eventually retry.
      throw IOException("Failed to decode bitmap.")
    }
    return bitmap
  }

  fun decodeResource(context: Context, request: Request): Bitmap {
    val resources = Utils.getResources(context, request)
    val id = Utils.getResourceId(resources, request)
    return if (VERSION.SDK_INT >= 28) {
      decodeResourceP(resources, id, request)
    } else {
      decodeResourcePreP(resources, id, request)
    }
  }

  @RequiresApi(28)
  private fun decodeResourceP(resources: Resources, @DrawableRes id: Int, request: Request): Bitmap {
    val imageSource = ImageDecoder.createSource(resources, id)
    return decodeImageSource(imageSource, request)
  }

  private fun decodeResourcePreP(resources: Resources, @DrawableRes id: Int, request: Request): Bitmap {
    val options = createBitmapOptions(request)
    if (requiresInSampleSize(options)) {
      BitmapFactory.decodeResource(resources, id, options)
      calculateInSampleSize(request.targetWidth, request.targetHeight, options!!, request)
    }
    return BitmapFactory.decodeResource(resources, id, options)
  }

  @RequiresApi(28)
  private fun decodeImageSource(imageSource: ImageDecoder.Source, request: Request): Bitmap {
    return ImageDecoder.decodeBitmap(imageSource) { imageDecoder, imageInfo, source ->
      imageDecoder.isMutableRequired = true
      if (request.hasSize()) {
        val size = imageInfo.size
        val width = size.width
        val height = size.height
        val targetWidth = request.targetWidth
        val targetHeight = request.targetHeight
        if (shouldResize(request.onlyScaleDown, width, height, targetWidth, targetHeight)) {
          val ratio = ratio(targetWidth, targetHeight, width, height, request)
          imageDecoder.setTargetSize(width / ratio, height / ratio)
        }
      }
    }
  }

  private fun ratio(
    requestWidth: Int,
    requestHeight: Int,
    width: Int,
    height: Int,
    request: Request
  ): Int =
    if (height > requestHeight || width > requestWidth) {
      val ratio = if (requestHeight == 0) {
        width / requestWidth
      } else if (requestWidth == 0) {
        height / requestHeight
      } else {
        val heightRatio = height / requestHeight
        val widthRatio = width / requestWidth
        if (request.centerInside)
          max(heightRatio, widthRatio)
        else
          min(heightRatio, widthRatio)
      }
      if (ratio != 0) ratio else 1
    } else {
      1
    }

  fun isXmlResource(resources: Resources, @DrawableRes drawableId: Int): Boolean {
    val typedValue = TypedValue()
    resources.getValue(drawableId, typedValue, true)
    val file = typedValue.string
    return file != null && file.toString().endsWith(".xml")
  }

  internal class ExceptionCatchingSource(delegate: Source) : ForwardingSource(delegate) {
    var thrownException: IOException? = null

    override fun read(sink: Buffer, byteCount: Long): Long {
      return try {
        super.read(sink, byteCount)
      } catch (e: IOException) {
        thrownException = e
        throw e
      }
    }

    fun throwIfCaught() {
      if (thrownException is IOException) {
        // TODO: Log when Android returns a non-null Bitmap after swallowing an IOException.
        // TODO: https://github.com/square/picasso/issues/2003/
        throw thrownException as IOException
      }
    }
  }
}
