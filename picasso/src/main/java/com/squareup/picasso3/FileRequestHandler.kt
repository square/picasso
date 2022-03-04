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
import com.squareup.picasso3.BitmapUtils.decodeStream
import com.squareup.picasso3.Picasso.LoadedFrom.DISK
import java.io.FileNotFoundException

internal class FileRequestHandler(context: Context) : ContentStreamRequestHandler(context) {
  override fun canHandleRequest(data: Request): Boolean {
    val uri = data.uri
    return uri != null && ContentResolver.SCHEME_FILE == uri.scheme
  }

  override fun load(
    picasso: Picasso,
    request: Request,
    callback: Callback
  ) {
    var signaledCallback = false
    try {
      val requestUri = checkNotNull(request.uri)
      val source = getSource(requestUri)
      val bitmap = decodeStream(source, request)
      val exifRotation = getExifOrientation(requestUri)
      signaledCallback = true
      callback.onSuccess(Result.Bitmap(bitmap, DISK, exifRotation))
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }

  override fun getExifOrientation(uri: Uri): Int {
    val path = uri.path ?: throw FileNotFoundException("path == null, uri: $uri")
    return ExifInterface(path).getAttributeInt(
      ExifInterface.TAG_ORIENTATION,
      ExifInterface.ORIENTATION_NORMAL
    )
  }
}
