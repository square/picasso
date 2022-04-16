/*
 * Copyright (C) 2022 Square, Inc.
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
import com.squareup.picasso3.RequestHandler.Result.Bitmap

internal class DrawableTargetAction(
  picasso: Picasso,
  private val target: DrawableTarget,
  data: Request,
  private val noFade: Boolean,
  private val placeholderDrawable: Drawable?,
  private val errorDrawable: Drawable?,
  @DrawableRes val errorResId: Int
) : Action(picasso, data) {
  override fun complete(result: Result) {
    if (result is Bitmap) {
      val bitmap = result.bitmap
      target.onDrawableLoaded(
        PicassoDrawable(
          context = picasso.context,
          bitmap = bitmap,
          placeholder = placeholderDrawable,
          loadedFrom = result.loadedFrom,
          noFade = noFade,
          debugging = picasso.indicatorsEnabled
        ),
        result.loadedFrom
      )
      check(!bitmap.isRecycled) { "Target callback must not recycle bitmap!" }
    }
  }

  override fun error(e: Exception) {
    val drawable = if (errorResId != 0) {
      ContextCompat.getDrawable(picasso.context, errorResId)
    } else {
      errorDrawable
    }

    target.onDrawableFailed(e, drawable)
  }

  override fun getTarget(): Any {
    return target
  }
}
