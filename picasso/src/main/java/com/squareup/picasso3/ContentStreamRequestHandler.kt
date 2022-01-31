/*
 * Copyright (C) 2013 Square, Inc.
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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
import androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION
import okio.BufferedSource
import okio.Source
import okio.buffer
import okio.source
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalStateException

internal open class ContentStreamRequestHandler(@JvmField val context: Context) : RequestHandler() {
  override fun canHandleRequest(data: Request): Boolean =
    ContentResolver.SCHEME_CONTENT == data.uri?.scheme ?: false

  override fun load(
    picasso: Picasso,
    request: Request,
    callback: Callback
  ) {

    var signaledCallback = false
    try {
      val requestUri = checkNotNull(request.uri)
      val source: BufferedSource = getSource(requestUri).buffer()
      val imageDecoder = request.decoderFactory!!.getImageDecoderForSource(source)
      if (imageDecoder == null) {
        callback.onError(IllegalStateException("No image decoder for request: $request"))
        return
      }
      val image = imageDecoder.decodeImage(source.buffer, request)!!
      image.bitmap?.let {
        callback.onSuccess(Result.Bitmap(it, Picasso.LoadedFrom.DISK, image.exifOrientation))
        signaledCallback = true
      }

      image.drawable?.let {
        callback.onSuccess(Result.Drawable(it, Picasso.LoadedFrom.DISK, image.exifOrientation))
        signaledCallback = true
      }
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }

  @Throws(FileNotFoundException::class) fun getSource(uri: Uri): Source {
    val contentResolver = context.contentResolver
    val inputStream = contentResolver.openInputStream(uri)
        ?: throw FileNotFoundException("can't open input stream, uri: $uri")
    return inputStream.source()
  }

  @Throws(IOException::class)
  protected open fun getExifOrientation(uri: Uri): Int {
    val contentResolver = context.contentResolver
    contentResolver.openInputStream(uri)?.use { input ->
      return ExifInterface(input).getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL)
    } ?: throw FileNotFoundException("can't open input stream, uri: $uri")
  }
}