package com.squareup.picasso3

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.Px
import com.squareup.picasso3.SizeSpec.Size
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface SizeSpec {

  suspend fun resolve(): Size

  sealed interface Size {
    object Unspecified : Size
    data class Exact(@Px val width: Int, @Px val height: Int) : Size
  }

  object Unspecified : SizeSpec {
    override suspend fun resolve() = Size.Unspecified
  }
}

internal class ImageViewSizeSpec(
  private val view: View,
) : SizeSpec {

  override suspend fun resolve(): Size {

    val startingWidth = view.width
    val startingHeight = view.height

    if (startingWidth > 0 && startingHeight > 0) {
      return Size.Exact(startingWidth, startingHeight)
    }

    return suspendCancellableCoroutine { continuation ->

      val vto = view.viewTreeObserver
      lateinit var attachStateChangeListener: View.OnAttachStateChangeListener

      val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          if (!vto.isAlive) return true

          val width = view.width
          val height = view.height

          if (width > 0 && height > 0) {
            view.removeOnAttachStateChangeListener(attachStateChangeListener)
            vto.removeOnPreDrawListener(this)
            continuation.resume(Size.Exact(width, height))
          }

          return true
        }
      }

      attachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
          view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        }

        override fun onViewDetachedFromWindow(view: View) {
          view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
      }

      view.addOnAttachStateChangeListener(attachStateChangeListener)

      // Only add the pre-draw listener if the view is already attached.
      // See: https://github.com/square/picasso/issues/1321
      if (view.windowToken != null) {
        attachStateChangeListener.onViewAttachedToWindow(view)
      }

      continuation.invokeOnCancellation {
        view.removeOnAttachStateChangeListener(attachStateChangeListener)
        vto.removeOnPreDrawListener(preDrawListener)
      }
    }
  }
}
