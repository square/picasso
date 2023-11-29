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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
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

@Composable
fun Picasso.rememberPainter(
  key: Any? = null,
  onError: ((Exception) -> Unit)? = null,
  request: (Picasso) -> RequestCreator
): Painter {
  return remember(key) { PicassoPainter(this, request, onError) }
}

internal class PicassoPainter(
  private val picasso: Picasso,
  private val request: (Picasso) -> RequestCreator,
  private val onError: ((Exception) -> Unit)? = null
) : Painter(), RememberObserver, DrawableTarget {

  private var lastRequestCreator: RequestCreator? by mutableStateOf(null)
  private val requestCreator: RequestCreator by derivedStateOf { request(picasso) }
  private var painter: Painter by mutableStateOf(EmptyPainter)
  private var alpha: Float by mutableStateOf(DefaultAlpha)
  private var colorFilter: ColorFilter? by mutableStateOf(null)

  override val intrinsicSize: Size
    get() {
      // Make sure we're using the latest request. If the request function reads any state, it will
      // invalidate whatever scope this property is being read from.
      load()
      return painter.intrinsicSize
    }

  override fun applyAlpha(alpha: Float): Boolean {
    this.alpha = alpha
    return true
  }

  override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
    this.colorFilter = colorFilter
    return true
  }

  override fun DrawScope.onDraw() {
    // Make sure we're using the latest request. If the request function reads any state, it will
    // invalidate this draw scope when it changes.
    load()
    with(painter) {
      draw(size, alpha, colorFilter)
    }
  }

  override fun onRemembered() {
    // This is called from composition, but if the request provider function reads any state we
    // don't want that to invalidate composition. It will invalidate draw, later.
    Snapshot.withoutReadObservation { load() }
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
  }

  override fun onDrawableLoaded(drawable: Drawable, from: LoadedFrom) {
    setPainter(drawable)
  }

  override fun onDrawableFailed(e: Exception, errorDrawable: Drawable?) {
    onError?.invoke(e)
    errorDrawable?.let(::setPainter)
  }

  private fun load() {
    // This derived state read will return the same instance of RequestCreator if one has been
    // cached and none of the state dependencies have since changed.
    val requestCreator = requestCreator
    // lastRequestCreator is just used for diffing, we don't want it to invalidate anything.
    val lastRequestCreator = Snapshot.withoutReadObservation { lastRequestCreator }

    // Only launch a new request if anything has actually changed. RequestCreator does not
    // currently implement an equals method, relying here on reference equality, future improvement
    // will be to implement equals which can prevent further re-requests.
    if (requestCreator != lastRequestCreator) {
      this.lastRequestCreator = requestCreator
      requestCreator.into(this)
    }
  }

  private fun setPainter(drawable: Drawable) {
    (painter as? RememberObserver)?.onForgotten()
    painter = DrawablePainter(drawable).apply(DrawablePainter::onRemembered)
  }
}

private object EmptyPainter : Painter() {
  override val intrinsicSize = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
}
