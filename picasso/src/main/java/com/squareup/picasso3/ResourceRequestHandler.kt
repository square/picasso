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
import com.squareup.picasso3.BitmapUtils.decodeResource
import com.squareup.picasso3.BitmapUtils.isXmlResource
import com.squareup.picasso3.Picasso.LoadedFrom.DISK

internal class ResourceRequestHandler(private val context: Context) : RequestHandler() {
  override fun canHandleRequest(data: Request): Boolean {
    return if (data.resourceId != 0 && !isXmlResource(context.resources, data.resourceId)) {
      true
    } else {
      data.uri != null && ContentResolver.SCHEME_ANDROID_RESOURCE == data.uri.scheme
    }
  }

  override fun load(
    picasso: Picasso,
    request: Request,
    callback: Callback
  ) {
    var signaledCallback = false
    try {
      val bitmap = decodeResource(context, request)
      signaledCallback = true
      callback.onSuccess(Result.Bitmap(bitmap, DISK))
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }
}
