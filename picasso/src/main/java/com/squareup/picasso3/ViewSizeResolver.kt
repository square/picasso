package com.squareup.picasso3

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.Px

interface SizeResolver {
  fun resolve(listener: (Size) -> Unit)
  fun onCancel()
}

data class Size(@Px val width: Int, @Px val height: Int)

class ViewSizeResolver(
  private val target: View,
) : SizeResolver, ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {

  private var listener: ((Size) -> Unit)? = null

  override fun resolve(listener: (Size) -> Unit) {
    val startingWidth = target.width
    val startingHeight = target.height

    if (startingWidth > 0 && startingHeight > 0) {
      listener(Size(startingWidth, startingHeight))
      return
    }

    this.listener = listener
    target.addOnAttachStateChangeListener(this)
    // Only add the pre-draw listener if the view is already attached.
    // See: https://github.com/square/picasso/issues/1321
    if (target.windowToken != null) {
      onViewAttachedToWindow(target)
    }
  }

  override fun onCancel() {
    listener = null
    target.removeOnAttachStateChangeListener(this)
    val vto = target.viewTreeObserver
    if (vto.isAlive) {
      vto.removeOnPreDrawListener(this)
    }
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
    listener?.invoke(Size(width, height))
    listener = null
    return true
  }

  override fun onViewAttachedToWindow(view: View) {
    view.viewTreeObserver.addOnPreDrawListener(this)
  }

  override fun onViewDetachedFromWindow(view: View) {
    view.viewTreeObserver.removeOnPreDrawListener(this)
  }
}
