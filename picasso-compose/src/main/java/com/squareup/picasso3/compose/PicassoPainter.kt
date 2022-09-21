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
package com.squareup.picasso3.compose

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import com.google.accompanist.drawablepainter.DrawablePainter
import com.squareup.picasso3.DrawableTarget
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Picasso.LoadedFrom
import com.squareup.picasso3.RequestCreator
import com.squareup.picasso3.compose.State.Error
import com.squareup.picasso3.compose.State.Loaded
import com.squareup.picasso3.compose.State.Loading

@Composable
fun Picasso.rememberPainter(
  key: Any? = null,
  onState: ((State) -> Unit)? = null,
  request: (Picasso) -> RequestCreator,
): Painter {
  return remember(key) { PicassoPainter(this, request, onState) }
}

internal class PicassoPainter(
  private val picasso: Picasso,
  private val request: (Picasso) -> RequestCreator,
  private val onState: ((State) -> Unit)? = null
) : Painter(), RememberObserver, DrawableTarget {

  private var painter: Painter by mutableStateOf(EmptyPainter)
  private var alpha: Float by mutableStateOf(DefaultAlpha)
  private var colorFilter: ColorFilter? by mutableStateOf(null)

  override val intrinsicSize: Size
    get() = painter.intrinsicSize

  override fun applyAlpha(alpha: Float): Boolean {
    this.alpha = alpha
    return true
  }

  override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
    this.colorFilter = colorFilter
    return true
  }

  override fun DrawScope.onDraw() {
    with(painter) {
      draw(size, alpha, colorFilter)
    }
  }

  override fun onRemembered() {
    request.invoke(picasso).into(this)
  }

  override fun onAbandoned() {
    (painter as? RememberObserver)?.onAbandoned()
    painter = EmptyPainter
    picasso.cancelRequest(this)
  }

  override fun onForgotten() {
    (painter as? RememberObserver)?.onForgotten()
    painter = EmptyPainter
    picasso.cancelRequest(this)
  }

  override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
    placeHolderDrawable?.let(::setPainter)
    onState?.invoke(Loading)
  }

  override fun onDrawableLoaded(drawable: Drawable, from: LoadedFrom) {
    setPainter(drawable)
    onState?.invoke(Loaded)
  }

  override fun onDrawableFailed(e: Exception, errorDrawable: Drawable?) {
    errorDrawable?.let(::setPainter)
    onState?.invoke(Error(e))
  }

  private fun setPainter(drawable: Drawable) {
    (painter as? RememberObserver)?.onForgotten()
    painter = DrawablePainter(drawable).apply(DrawablePainter::onRemembered)
  }
}

sealed class State {
  object Loading : State()
  object Loaded : State()
  class Error(exception: Exception) : State()
}

private object EmptyPainter : Painter() {
  override val intrinsicSize = Size.Zero
  override fun DrawScope.onDraw() = Unit
}
