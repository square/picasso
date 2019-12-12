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

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.squareup.picasso3.RequestHandler.Result

internal class BitmapTargetAction(
  picasso: Picasso,
  val target: BitmapTarget,
  data: Request,
  val errorDrawable: Drawable?,
  @DrawableRes val errorResId: Int
) : Action(picasso, data) {
  override fun complete(result: Result) {
    val bitmap = result.bitmap
    if (bitmap != null) {
      target.onBitmapLoaded(bitmap, result.loadedFrom)
      check(!bitmap.isRecycled) { "Target callback must not recycle bitmap!" }
    }
  }

  override fun error(e: Exception) {
    if (errorResId != 0) {
      target.onBitmapFailed(e, ContextCompat.getDrawable(picasso.context, errorResId))
    } else {
      target.onBitmapFailed(e, errorDrawable)
    }
  }

  override fun getTarget(): Any {
    return target
  }
}
