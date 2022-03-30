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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Video
import com.squareup.picasso3.BitmapUtils.calculateInSampleSize
import com.squareup.picasso3.BitmapUtils.createBitmapOptions
import com.squareup.picasso3.BitmapUtils.decodeStream
import com.squareup.picasso3.Picasso.LoadedFrom

internal class MediaStoreRequestHandler(context: Context) : ContentStreamRequestHandler(context) {
  override fun canHandleRequest(data: Request): Boolean {
    val uri = data.uri
    return uri != null &&
      ContentResolver.SCHEME_CONTENT == uri.scheme &&
      MediaStore.AUTHORITY == uri.authority
  }

  override fun load(picasso: Picasso, request: Request, callback: Callback) {
    var signaledCallback = false
    try {
      val contentResolver = context.contentResolver
      val requestUri = checkNotNull(request.uri, { "request.uri == null" })
      val exifOrientation = getExifOrientation(requestUri)

      val mimeType = contentResolver.getType(requestUri)
      val isVideo = mimeType != null && mimeType.startsWith("video/")

      if (request.hasSize()) {
        val picassoKind = getPicassoKind(request.targetWidth, request.targetHeight)
        if (!isVideo && picassoKind == PicassoKind.FULL) {
          val source = getSource(requestUri)
          val bitmap = decodeStream(source, request)
          signaledCallback = true
          callback.onSuccess(Result.Bitmap(bitmap, LoadedFrom.DISK, exifOrientation))
          return
        }

        val id = ContentUris.parseId(requestUri)

        val options = checkNotNull(createBitmapOptions(request), { "options == null" })
        options.inJustDecodeBounds = true

        calculateInSampleSize(
          request.targetWidth, request.targetHeight, picassoKind.width,
          picassoKind.height, options, request
        )

        val bitmap = if (isVideo) {
          // Since MediaStore doesn't provide the full screen kind thumbnail, we use the mini kind
          // instead which is the largest thumbnail size can be fetched from MediaStore.
          val kind =
            if (picassoKind == PicassoKind.FULL) Video.Thumbnails.MINI_KIND else picassoKind.androidKind
          Video.Thumbnails.getThumbnail(contentResolver, id, kind, options)
        } else {
          MediaStore.Images.Thumbnails.getThumbnail(
            contentResolver,
            id,
            picassoKind.androidKind,
            options
          )
        }

        if (bitmap != null) {
          signaledCallback = true
          callback.onSuccess(Result.Bitmap(bitmap, LoadedFrom.DISK, exifOrientation))
          return
        }
      }

      val source = getSource(requestUri)
      val bitmap = decodeStream(source, request)
      signaledCallback = true
      callback.onSuccess(Result.Bitmap(bitmap, LoadedFrom.DISK, exifOrientation))
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }

  internal enum class PicassoKind(val androidKind: Int, val width: Int, val height: Int) {
    MICRO(MediaStore.Images.Thumbnails.MICRO_KIND, 96, 96),
    MINI(MediaStore.Images.Thumbnails.MINI_KIND, 512, 384),
    FULL(MediaStore.Images.Thumbnails.FULL_SCREEN_KIND, -1, -1);
  }

  companion object {
    fun getPicassoKind(targetWidth: Int, targetHeight: Int): PicassoKind {
      return if (targetWidth <= PicassoKind.MICRO.width && targetHeight <= PicassoKind.MICRO.height) {
        PicassoKind.MICRO
      } else if (targetWidth <= PicassoKind.MINI.width && targetHeight <= PicassoKind.MINI.height) {
        PicassoKind.MINI
      } else {
        PicassoKind.FULL
      }
    }
  }
}
