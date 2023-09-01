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

@Composable
fun Picasso.rememberPainter(
  key: Any? = null,
  onError: ((Exception) -> Unit)? = null,
  request: (Picasso) -> RequestCreator
): Painter {
  return remember(key) { PicassoPainter(this, request, onError) }
    .also { painter ->
      DisposableEffect(painter) {
        painter.start()
        onDispose(painter::stop)
      }
    }
}

internal class PicassoPainter(
  private val picasso: Picasso,
  request: (Picasso) -> RequestCreator,
  private val onError: ((Exception) -> Unit)? = null
) : Painter(), RememberObserver, DrawableTarget {

  private val stateObserver = SnapshotStateObserver(onChangeExecutor = { callback ->
      if (Looper.myLooper == Looper.mainLooper) {
        callback()
      } else {
        postToMainThread(callback)
      }
    })
  private val request: RequestCreator by derivedStateOf { request.invoke(picasso) }
  // This is technically written to from composition once, by onRemembered. However it's
  // never written to from composition again so it doesn't need to be snapshot state.
  private var lastRequest: RequestCreator? = null
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
    loadRequest()
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

  fun start() {
    stateObserver.start()
    // Even though loadRequest was called in onRemembered, we need to call it
    // again now that the observer is started to observe state reads.
    loadRequest()
  }

  fun stop() {
    stateObserver.stop()
    stateObserver.clear()
    // Picasso state will be cleaned up by onForgotten, we don't need to
    // do it here.
  }

  private fun loadRequest() {
    // Because this will get called from composition, and we don't care about
    // lastRequest changing and are handling request reads ourself.
    // It will get called again from start() and that will observe the reads.
    Snapshot.withoutReadObservation {
      // The first time this is called, from composition, the observer won't
      // have been started yet so this will run the block but not observe it.
      val newRequest = stateObserver.observeReads(this, onCommitAffectingRequest) {
        request
      }
      // Can be != if RequestCreators are comparable. Identity comparison works
      // because derivedStateOf will return a cached instance if nothing changed.
      if (newRequest !== lastRequest) {
        lastRequest = newRequest
        newRequest.into(this)
      }
    }
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

  private fun setPainter(drawable: Drawable) {
    (painter as? RememberObserver)?.onForgotten()
    painter = DrawablePainter(drawable).apply(DrawablePainter::onRemembered)
  }

  private companion object {
    val onCommitAffectingRequest: (PicassoPainter) -> Unit = { painter ->
      painter.loadRequest()
    }
  }
}

private object EmptyPainter : Painter() {
  override val intrinsicSize = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
}
