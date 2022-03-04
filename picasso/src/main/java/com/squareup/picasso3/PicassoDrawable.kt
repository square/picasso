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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.widget.ImageView
import com.squareup.picasso3.Picasso.LoadedFrom
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.RequestHandler.Result

internal class PicassoDrawable(
  context: Context,
  bitmap: Bitmap,
  placeholder: Drawable?,
  private val loadedFrom: LoadedFrom,
  noFade: Boolean,
  private val debugging: Boolean
) : BitmapDrawable(context.resources, bitmap) {
  private val density: Float = context.resources.displayMetrics.density
  var placeholder: Drawable? = null
  var startTimeMillis: Long = 0
  var animating = false
  private var _alpha = 0xFF

  init {
    val fade = loadedFrom != MEMORY && !noFade
    if (fade) {
      this.placeholder = placeholder
      animating = true
      startTimeMillis = SystemClock.uptimeMillis()
    }
  }

  override fun draw(canvas: Canvas) {
    if (!animating) {
      super.draw(canvas)
    } else {
      val normalized = (SystemClock.uptimeMillis() - startTimeMillis) / FADE_DURATION
      if (normalized >= 1f) {
        animating = false
        placeholder = null
        super.draw(canvas)
      } else {
        if (placeholder != null) {
          placeholder!!.draw(canvas)
        }

        // setAlpha will call invalidateSelf and drive the animation.
        val partialAlpha = (_alpha * normalized).toInt()
        super.setAlpha(partialAlpha)
        super.draw(canvas)
        super.setAlpha(_alpha)
      }
    }

    if (debugging) {
      drawDebugIndicator(canvas)
    }
  }

  override fun setAlpha(alpha: Int) {
    this._alpha = alpha
    if (placeholder != null) {
      placeholder!!.alpha = alpha
    }
    super.setAlpha(alpha)
  }

  override fun setColorFilter(cf: ColorFilter?) {
    if (placeholder != null) {
      placeholder!!.colorFilter = cf
    }
    super.setColorFilter(cf)
  }

  override fun onBoundsChange(bounds: Rect) {
    if (placeholder != null) {
      placeholder!!.bounds = bounds
    }
    super.onBoundsChange(bounds)
  }

  private fun drawDebugIndicator(canvas: Canvas) {
    DEBUG_PAINT.color = Color.WHITE
    var path = getTrianglePath(0, 0, (16 * density).toInt())
    canvas.drawPath(path, DEBUG_PAINT)

    DEBUG_PAINT.color = loadedFrom.debugColor
    path = getTrianglePath(0, 0, (15 * density).toInt())
    canvas.drawPath(path, DEBUG_PAINT)
  }

  companion object {
    // Only accessed from main thread.
    private val DEBUG_PAINT = Paint()
    private const val FADE_DURATION = 200f // ms

    /**
     * Create or update the drawable on the target [ImageView] to display the supplied bitmap
     * image.
     */
    fun setResult(
      target: ImageView,
      context: Context,
      result: Result,
      noFade: Boolean,
      debugging: Boolean
    ) {
      val placeholder = target.drawable
      if (placeholder is Animatable) {
        (placeholder as Animatable).stop()
      }

      if (result is Result.Bitmap) {
        val bitmap = result.bitmap
        val loadedFrom = result.loadedFrom
        val drawable = PicassoDrawable(context, bitmap, placeholder, loadedFrom, noFade, debugging)
        target.setImageDrawable(drawable)
      } else {
        val drawable = (result as Result.Drawable).drawable
        target.setImageDrawable(drawable)
        if (drawable is Animatable) {
          (drawable as Animatable).start()
        }
      }
    }

    /**
     * Create or update the drawable on the target [ImageView] to display the supplied
     * placeholder image.
     */
    fun setPlaceholder(target: ImageView, placeholderDrawable: Drawable?) {
      target.setImageDrawable(placeholderDrawable)
      if (target.drawable is Animatable) {
        (target.drawable as Animatable).start()
      }
    }

    fun getTrianglePath(x1: Int, y1: Int, width: Int): Path {
      return Path().apply {
        moveTo(x1.toFloat(), y1.toFloat())
        lineTo((x1 + width).toFloat(), y1.toFloat())
        lineTo(x1.toFloat(), (y1 + width).toFloat())
      }
    }
  }
}
