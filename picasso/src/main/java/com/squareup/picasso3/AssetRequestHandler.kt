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
import android.content.res.AssetManager
import com.squareup.picasso3.BitmapUtils.decodeStream
import com.squareup.picasso3.Picasso.LoadedFrom.DISK
import okio.source

internal class AssetRequestHandler(private val context: Context) : RequestHandler() {
  private val lock = Any()

  @Volatile
  private var assetManager: AssetManager? = null

  override fun canHandleRequest(data: Request): Boolean {
    val uri = data.uri
    return uri != null &&
      ContentResolver.SCHEME_FILE == uri.scheme &&
      uri.pathSegments.isNotEmpty() &&
      ANDROID_ASSET == uri.pathSegments[0]
  }

  override fun load(
    picasso: Picasso,
    request: Request,
    callback: Callback
  ) {
    initializeIfFirstTime()
    var signaledCallback = false
    try {
      assetManager!!.open(getFilePath(request))
        .source()
        .use { source ->
          val bitmap = decodeStream(source, request)
          signaledCallback = true
          callback.onSuccess(Result.Bitmap(bitmap, DISK))
        }
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }

  @Initializer private fun initializeIfFirstTime() {
    if (assetManager == null) {
      synchronized(lock) {
        if (assetManager == null) {
          assetManager = context.assets
        }
      }
    }
  }

  companion object {
    private const val ANDROID_ASSET = "android_asset"
    private const val ASSET_PREFIX_LENGTH =
      "${ContentResolver.SCHEME_FILE}:///$ANDROID_ASSET/".length

    fun getFilePath(request: Request): String {
      val uri = checkNotNull(request.uri)
      return uri.toString()
        .substring(ASSET_PREFIX_LENGTH)
    }
  }
}
