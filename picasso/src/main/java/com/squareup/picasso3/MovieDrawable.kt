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

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock

internal class MovieDrawable(
  private val movie: Movie
) : Drawable(), Animatable {
  private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var start = 0

  override fun draw(canvas: Canvas) {
    if (start > 0) {
      movie.setTime(((SystemClock.uptimeMillis() - start).toInt()) % movie.duration())
      invalidateSelf()
    }

    movie.draw(canvas, 0f, 0f, paint)
  }

  override fun setAlpha(alpha: Int) {
    paint.alpha = alpha
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    paint.colorFilter = colorFilter
  }

  override fun getOpacity(): Int {
    return PixelFormat.OPAQUE
  }

  override fun getIntrinsicWidth(): Int {
    return movie.width()
  }

  override fun getIntrinsicHeight(): Int {
    return movie.height()
  }

  override fun start() {
    start = SystemClock.uptimeMillis().toInt()
    invalidateSelf()
  }

  override fun stop() {
    start = 0
  }

  override fun isRunning() = start != 0
}
