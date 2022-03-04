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

import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewTreeObserver
import android.widget.ImageView

internal class DeferredRequestCreator(
  private val creator: RequestCreator,
  internal val target: ImageView,
  internal var callback: Callback?
) : ViewTreeObserver.OnPreDrawListener, OnAttachStateChangeListener {
  init {
    target.addOnAttachStateChangeListener(this)

    // Only add the pre-draw listener if the view is already attached.
    // See: https://github.com/square/picasso/issues/1321
    if (target.windowToken != null) {
      onViewAttachedToWindow(target)
    }
  }

  override fun onViewAttachedToWindow(view: View) {
    view.viewTreeObserver.addOnPreDrawListener(this)
  }

  override fun onViewDetachedFromWindow(view: View) {
    view.viewTreeObserver.removeOnPreDrawListener(this)
  }

  override fun onPreDraw(): Boolean {
    val vto = target.viewTreeObserver
    if (!vto.isAlive) {
      return true
    }

    val width = target.width
    val height = target.height

    if (width <= 0 || height <= 0) {
      return true
    }

    target.removeOnAttachStateChangeListener(this)
    vto.removeOnPreDrawListener(this)

    creator.unfit().resize(width, height).into(target, callback)
    return true
  }

  fun cancel() {
    creator.clearTag()
    callback = null

    target.removeOnAttachStateChangeListener(this)

    val vto = target.viewTreeObserver
    if (vto.isAlive) {
      vto.removeOnPreDrawListener(this)
    }
  }

  val tag: Any?
    get() = creator.tag
}
