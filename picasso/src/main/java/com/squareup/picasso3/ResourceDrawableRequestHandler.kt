/*
 * Copyright (C) 2018 Square, Inc.
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

import android.content.Context
import androidx.core.content.ContextCompat
import com.squareup.picasso3.BitmapUtils.isXmlResource
import com.squareup.picasso3.Picasso.LoadedFrom.DISK

internal class ResourceDrawableRequestHandler private constructor(
  private val context: Context,
  private val loader: DrawableLoader
) : RequestHandler() {
  override fun canHandleRequest(data: Request): Boolean {
    return data.resourceId != 0 && isXmlResource(context.resources, data.resourceId)
  }

  override fun load(
    picasso: Picasso,
    request: Request,
    callback: Callback
  ) {
    val drawable = loader.load(request.resourceId)
    if (drawable == null) {
      callback.onError(
        IllegalArgumentException("invalid resId: ${Integer.toHexString(request.resourceId)}")
      )
    } else {
      callback.onSuccess(Result.Drawable(drawable, DISK))
    }
  }

  internal companion object {
    @JvmName("-create")
    internal fun create(
      context: Context,
      loader: DrawableLoader = DrawableLoader { resId -> ContextCompat.getDrawable(context, resId) }
    ) = ResourceDrawableRequestHandler(context, loader)
  }
}
