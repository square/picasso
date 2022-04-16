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

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.Gravity
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import com.squareup.picasso3.BitmapHunter.Companion.forRequest
import com.squareup.picasso3.MemoryPolicy.Companion.shouldReadFromMemoryCache
import com.squareup.picasso3.MemoryPolicy.Companion.shouldWriteToMemoryCache
import com.squareup.picasso3.Picasso.LoadedFrom
import com.squareup.picasso3.PicassoDrawable.Companion.setPlaceholder
import com.squareup.picasso3.PicassoDrawable.Companion.setResult
import com.squareup.picasso3.RemoteViewsAction.AppWidgetAction
import com.squareup.picasso3.RemoteViewsAction.NotificationAction
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget
import com.squareup.picasso3.Utils.OWNER_MAIN
import com.squareup.picasso3.Utils.VERB_COMPLETED
import com.squareup.picasso3.Utils.checkMain
import com.squareup.picasso3.Utils.checkNotMain
import com.squareup.picasso3.Utils.log
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Fluent API for building an image download request.  */
class RequestCreator internal constructor(
  private val picasso: Picasso,
  uri: Uri?,
  resourceId: Int
) {
  private val data = Request.Builder(uri, resourceId, picasso.defaultBitmapConfig)

  private var noFade = false
  private var deferred = false
  private var setPlaceholder = true
  @DrawableRes private var placeholderResId = 0
  @DrawableRes private var errorResId = 0
  private var placeholderDrawable: Drawable? = null
  private var errorDrawable: Drawable? = null

  /** Internal use only. Used by [DeferredRequestCreator].  */
  @get:JvmName("-tag")
  internal val tag: Any?
    get() = data.tag

  init {
    check(!picasso.shutdown) { "Picasso instance already shut down. Cannot submit new requests." }
  }

  /**
   * Explicitly opt-out to having a placeholder set when calling [into].
   *
   * By default, Picasso will either set a supplied placeholder or clear the target
   * [ImageView] in order to ensure behavior in situations where views are recycled. This
   * method will prevent that behavior and retain any already set image.
   */
  fun noPlaceholder(): RequestCreator {
    check(placeholderResId == 0) { "Placeholder resource already set." }
    check(placeholderDrawable == null) { "Placeholder image already set." }
    setPlaceholder = false
    return this
  }

  /**
   * A placeholder drawable to be used while the image is being loaded. If the requested image is
   * not immediately available in the memory cache then this resource will be set on the target
   * [ImageView].
   */
  fun placeholder(@DrawableRes placeholderResId: Int): RequestCreator {
    check(setPlaceholder) { "Already explicitly declared as no placeholder." }
    require(placeholderResId != 0) { "Placeholder image resource invalid." }
    check(placeholderDrawable == null) { "Placeholder image already set." }
    this.placeholderResId = placeholderResId
    return this
  }

  /**
   * A placeholder drawable to be used while the image is being loaded. If the requested image is
   * not immediately available in the memory cache then this resource will be set on the target
   * [ImageView].
   *
   * If you are not using a placeholder image but want to clear an existing image (such as when
   * used in an [adapter][android.widget.Adapter]), pass in `null`.
   */
  fun placeholder(placeholderDrawable: Drawable?): RequestCreator {
    check(setPlaceholder) { "Already explicitly declared as no placeholder." }
    check(placeholderResId == 0) { "Placeholder image already set." }
    this.placeholderDrawable = placeholderDrawable
    return this
  }

  /** An error drawable to be used if the request image could not be loaded.  */
  fun error(@DrawableRes errorResId: Int): RequestCreator {
    require(errorResId != 0) { "Error image resource invalid." }
    check(errorDrawable == null) { "Error image already set." }
    this.errorResId = errorResId
    return this
  }

  /** An error drawable to be used if the request image could not be loaded.  */
  fun error(errorDrawable: Drawable): RequestCreator {
    check(errorResId == 0) { "Error image already set." }
    this.errorDrawable = errorDrawable
    return this
  }

  /**
   * Assign a tag to this request. Tags are an easy way to logically associate
   * related requests that can be managed together e.g. paused, resumed,
   * or canceled.
   *
   * You can either use simple [String] tags or objects that naturally
   * define the scope of your requests within your app such as a
   * [android.content.Context], an [android.app.Activity], or a
   * [android.app.Fragment].
   *
   * **WARNING:**: Picasso will keep a reference to the tag for
   * as long as this tag is paused and/or has active requests. Look out for
   * potential leaks.
   *
   * @see Picasso.cancelTag
   * @see Picasso.pauseTag
   * @see Picasso.resumeTag
   */
  fun tag(tag: Any): RequestCreator {
    data.tag(tag)
    return this
  }

  /**
   * Attempt to resize the image to fit exactly into the target [ImageView]'s bounds. This
   * will result in delayed execution of the request until the [ImageView] has been laid out.
   *
   * *Note:* This method works only when your target is an [ImageView].
   */
  fun fit(): RequestCreator {
    deferred = true
    return this
  }

  /** Internal use only. Used by [DeferredRequestCreator].  */
  @JvmName("-unfit")
  internal fun unfit(): RequestCreator {
    deferred = false
    return this
  }

  /** Internal use only. Used by [DeferredRequestCreator].  */
  @JvmName("-clearTag")
  internal fun clearTag(): RequestCreator {
    data.clearTag()
    return this
  }

  /**
   * Resize the image to the specified dimension size.
   * Use 0 as desired dimension to resize keeping aspect ratio.
   */
  fun resizeDimen(
    @DimenRes targetWidthResId: Int,
    @DimenRes targetHeightResId: Int,
  ): RequestCreator {
    val resources = picasso.context.resources
    val targetWidth = resources.getDimensionPixelSize(targetWidthResId)
    val targetHeight = resources.getDimensionPixelSize(targetHeightResId)
    return resize(targetWidth, targetHeight)
  }

  /**
   * Resize the image to the specified size in pixels.
   * Use 0 as desired dimension to resize keeping aspect ratio.
   */
  fun resize(targetWidth: Int, targetHeight: Int): RequestCreator {
    data.resize(targetWidth, targetHeight)
    return this
  }

  /**
   * Crops an image inside of the bounds specified by [resize] rather than
   * distorting the aspect ratio. This cropping technique scales the image so that it fills the
   * requested bounds and then crops the extra.
   */
  fun centerCrop(): RequestCreator {
    data.centerCrop(Gravity.CENTER)
    return this
  }

  /**
   * Crops an image inside of the bounds specified by [resize] rather than
   * distorting the aspect ratio. This cropping technique scales the image so that it fills the
   * requested bounds and then crops the extra, preferring the contents at [alignGravity].
   */
  fun centerCrop(alignGravity: Int): RequestCreator {
    data.centerCrop(alignGravity)
    return this
  }

  /**
   * Centers an image inside of the bounds specified by [resize]. This scales
   * the image so that both dimensions are equal to or less than the requested bounds.
   */
  fun centerInside(): RequestCreator {
    data.centerInside()
    return this
  }

  /**
   * Only resize an image if the original image size is bigger than the target size
   * specified by [resize].
   */
  fun onlyScaleDown(): RequestCreator {
    data.onlyScaleDown()
    return this
  }

  /** Rotate the image by the specified degrees.  */
  fun rotate(degrees: Float): RequestCreator {
    data.rotate(degrees)
    return this
  }

  /** Rotate the image by the specified degrees around a pivot point.  */
  fun rotate(degrees: Float, pivotX: Float, pivotY: Float): RequestCreator {
    data.rotate(degrees, pivotX, pivotY)
    return this
  }

  /**
   * Attempt to decode the image using the specified config.
   *
   * Note: This value may be ignored by [BitmapFactory]. See
   * [its documentation][BitmapFactory.Options.inPreferredConfig] for more details.
   */
  fun config(config: Bitmap.Config): RequestCreator {
    data.config(config)
    return this
  }

  /**
   * Sets the stable key for this request to be used instead of the URI or resource ID when
   * caching. Two requests with the same value are considered to be for the same resource.
   */
  fun stableKey(stableKey: String): RequestCreator {
    data.stableKey(stableKey)
    return this
  }

  /**
   * Set the priority of this request.
   *
   *
   * This will affect the order in which the requests execute but does not guarantee it.
   * By default, all requests have [Priority.NORMAL] priority, except for
   * [fetch] requests, which have [Priority.LOW] priority by default.
   */
  fun priority(priority: Picasso.Priority): RequestCreator {
    data.priority(priority)
    return this
  }

  /**
   * Add a custom transformation to be applied to the image.
   *
   * Custom transformations will always be run after the built-in transformations.
   */
  // TODO show example of calling resize after a transform in the javadoc
  fun transform(transformation: Transformation): RequestCreator {
    data.transform(transformation)
    return this
  }

  /**
   * Add a list of custom transformations to be applied to the image.
   *
   * Custom transformations will always be run after the built-in transformations.
   */
  fun transform(transformations: List<Transformation>): RequestCreator {
    data.transform(transformations)
    return this
  }

  /**
   * Specifies the [MemoryPolicy] to use for this request. You may specify additional policy
   * options using the varargs parameter.
   */
  fun memoryPolicy(
    policy: MemoryPolicy,
    vararg additional: MemoryPolicy,
  ): RequestCreator {
    data.memoryPolicy(policy, *additional)
    return this
  }

  /**
   * Specifies the [NetworkPolicy] to use for this request. You may specify additional policy
   * options using the varargs parameter.
   */
  fun networkPolicy(
    policy: NetworkPolicy,
    vararg additional: NetworkPolicy,
  ): RequestCreator {
    data.networkPolicy(policy, *additional)
    return this
  }

  /**
   * Add custom HTTP headers to the image network request, if desired
   */
  fun addHeader(key: String, value: String): RequestCreator {
    data.addHeader(key, value)
    return this
  }

  /** Disable brief fade in of images loaded from the disk cache or network.  */
  fun noFade(): RequestCreator {
    noFade = true
    return this
  }

  /**
   * Synchronously fulfill this request. Must not be called from the main thread.
   */
  @Throws(IOException::class) // TODO make non-null and always throw?
  fun get(): Bitmap? {
    val started = System.nanoTime()
    checkNotMain()
    check(!deferred) { "Fit cannot be used with get." }
    if (!data.hasImage()) {
      return null
    }

    val request = createRequest(started)
    val action = GetAction(picasso, request)
    val result =
      forRequest(picasso, picasso.dispatcher, picasso.cache, action).hunt() ?: return null

    val bitmap = result.bitmap
    if (shouldWriteToMemoryCache(request.memoryPolicy)) {
      picasso.cache[request.key] = bitmap
    }

    return bitmap
  }
  /**
   * Asynchronously fulfills the request without a [ImageView] or [BitmapTarget],
   * and invokes the target [Callback] with the result. This is useful when you want to warm
   * up the cache with an image.
   *
   * *Note:* The [Callback] param is a strong reference and will prevent your
   * [android.app.Activity] or [android.app.Fragment] from being garbage collected
   * until the request is completed.
   *
   * *Note:* It is safe to invoke this method from any thread.
   */
  @JvmOverloads fun fetch(callback: Callback? = null) {
    val started = System.nanoTime()
    check(!deferred) { "Fit cannot be used with fetch." }

    if (data.hasImage()) {
      // Fetch requests have lower priority by default.
      if (!data.hasPriority()) {
        data.priority(Picasso.Priority.LOW)
      }

      val request = createRequest(started)
      if (shouldReadFromMemoryCache(request.memoryPolicy)) {
        val bitmap = picasso.quickMemoryCacheCheck(request.key)
        if (bitmap != null) {
          if (picasso.isLoggingEnabled) {
            log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + LoadedFrom.MEMORY)
          }
          callback?.onSuccess()
          return
        }
      }

      val action = FetchAction(picasso, request, callback)
      picasso.submit(action)
    }
  }

  /**
   * Asynchronously fulfills the request into the specified [BitmapTarget]. In most cases, you
   * should use this when you are dealing with a custom [View][android.view.View] or view
   * holder which should implement the [BitmapTarget] interface.
   *
   * Implementing on a [View][android.view.View]:
   * ```
   * class ProfileView(context: Context) : FrameLayout(context), Target {
   *   override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom) {
   *     setBackgroundDrawable(BitmapDrawable(bitmap))
   *   }
   *
   *   override run onBitmapFailed(e: Exception, errorDrawable: Drawable) {
   *     setBackgroundDrawable(errorDrawable)
   *   }
   *
   *   override fun onPrepareLoad(placeholderDrawable: Drawable) {
   *     setBackgroundDrawable(placeholderDrawable
   *   }
   * }
   * ```
   */
  fun into(target: BitmapTarget) {
    val started = System.nanoTime()
    checkMain()
    check(!deferred) { "Fit cannot be used with a Target." }

    if (!data.hasImage()) {
      picasso.cancelRequest(target)
      target.onPrepareLoad(if (setPlaceholder) getPlaceholderDrawable() else null)
      return
    }

    val request = createRequest(started)
    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      val bitmap = picasso.quickMemoryCacheCheck(request.key)
      if (bitmap != null) {
        picasso.cancelRequest(target)
        target.onBitmapLoaded(bitmap, LoadedFrom.MEMORY)
        return
      }
    }

    target.onPrepareLoad(if (setPlaceholder) getPlaceholderDrawable() else null)
    val action = BitmapTargetAction(picasso, target, request, errorDrawable, errorResId)
    picasso.enqueueAndSubmit(action)
  }

  /**
   * Asynchronously fulfills the request into the specified [DrawableTarget]. In most cases, you
   * should use this when you are dealing with a custom [View][android.view.View] or view
   * holder which should implement the [DrawableTarget] interface.
   */
  fun into(target: DrawableTarget) {
    val started = System.nanoTime()
    checkMain()
    check(!deferred) { "Fit cannot be used with a Target." }

    val placeHolderDrawable = if (setPlaceholder) getPlaceholderDrawable() else null
    if (!data.hasImage()) {
      picasso.cancelRequest(target)
      target.onPrepareLoad(placeHolderDrawable)
      return
    }

    val request = createRequest(started)
    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      val bitmap = picasso.quickMemoryCacheCheck(request.key)
      if (bitmap != null) {
        picasso.cancelRequest(target)
        target.onDrawableLoaded(
          PicassoDrawable(
            context = picasso.context,
            bitmap = bitmap,
            placeholder = null,
            loadedFrom = LoadedFrom.MEMORY,
            noFade = noFade,
            debugging = picasso.indicatorsEnabled
          ),
          LoadedFrom.MEMORY
        )
        return
      }
    }

    target.onPrepareLoad(placeHolderDrawable)
    val action = DrawableTargetAction(picasso, target, request, noFade, placeHolderDrawable, errorDrawable, errorResId)
    picasso.enqueueAndSubmit(action)
  }

  /**
   * Asynchronously fulfills the request into the specified [RemoteViews] object with the
   * given [viewId]. This is used for loading bitmaps into a [Notification].
   */
  @JvmOverloads
  fun into(
    remoteViews: RemoteViews,
    @IdRes viewId: Int,
    notificationId: Int,
    notification: Notification,
    notificationTag: String? = null,
    callback: Callback? = null,
  ) {
    val started = System.nanoTime()
    check(!deferred) { "Fit cannot be used with RemoteViews." }
    require(!(placeholderDrawable != null || errorDrawable != null)) {
      "Cannot use placeholder or error drawables with remote views."
    }

    val request = createRequest(started)
    val action = NotificationAction(
      picasso,
      request,
      errorResId,
      RemoteViewsTarget(remoteViews, viewId),
      notificationId,
      notification,
      notificationTag,
      callback
    )
    performRemoteViewInto(request, action)
  }

  /**
   * Asynchronously fulfills the request into the specified [RemoteViews] object with the
   * given [viewId]. This is used for loading bitmaps into all instances of a widget.
   */
  fun into(
    remoteViews: RemoteViews,
    @IdRes viewId: Int,
    appWidgetId: Int,
    callback: Callback? = null,
  ) {
    into(remoteViews, viewId, intArrayOf(appWidgetId), callback)
  }

  /**
   * Asynchronously fulfills the request into the specified [RemoteViews] object with the
   * given [viewId]. This is used for loading bitmaps into all instances of a widget.
   */
  @JvmOverloads
  fun into(
    remoteViews: RemoteViews,
    @IdRes viewId: Int,
    appWidgetIds: IntArray,
    callback: Callback? = null,
  ) {
    val started = System.nanoTime()
    check(!deferred) { "Fit cannot be used with remote views." }
    require(!(placeholderDrawable != null || errorDrawable != null)) {
      "Cannot use placeholder or error drawables with remote views."
    }

    val request = createRequest(started)
    val action = AppWidgetAction(
      picasso,
      request,
      errorResId,
      RemoteViewsTarget(remoteViews, viewId),
      appWidgetIds,
      callback
    )

    performRemoteViewInto(request, action)
  }

  /**
   * Asynchronously fulfills the request into the specified [ImageView] and invokes the
   * target [Callback] if it's not `null`.
   *
   * *Note:* The [Callback] param is a strong reference and will prevent your
   * [android.app.Activity] or [android.app.Fragment] from being garbage collected. If
   * you use this method, it is **strongly** recommended you invoke an adjacent
   * [Picasso.cancelRequest] call to prevent temporary leaking.
   *
   * *Note:* This method will automatically support object recycling.
   */
  @JvmOverloads fun into(target: ImageView, callback: Callback? = null) {
    val started = System.nanoTime()
    checkMain()

    if (!data.hasImage()) {
      picasso.cancelRequest(target)
      if (setPlaceholder) {
        setPlaceholder(target, getPlaceholderDrawable())
      }
      return
    }

    if (deferred) {
      check(!data.hasSize()) { "Fit cannot be used with resize." }
      val width = target.width
      val height = target.height
      if (width == 0 || height == 0) {
        if (setPlaceholder) {
          setPlaceholder(target, getPlaceholderDrawable())
        }
        picasso.defer(target, DeferredRequestCreator(this, target, callback))
        return
      }
      data.resize(width, height)
    }

    val request = createRequest(started)

    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      val bitmap = picasso.quickMemoryCacheCheck(request.key)
      if (bitmap != null) {
        picasso.cancelRequest(target)
        val result: RequestHandler.Result = RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY)
        setResult(target, picasso.context, result, noFade, picasso.indicatorsEnabled)
        if (picasso.isLoggingEnabled) {
          log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + LoadedFrom.MEMORY)
        }
        callback?.onSuccess()
        return
      }
    }

    if (setPlaceholder) {
      setPlaceholder(target, getPlaceholderDrawable())
    }

    val action = ImageViewAction(
      picasso,
      target,
      request,
      errorDrawable,
      errorResId,
      noFade,
      callback
    )

    picasso.enqueueAndSubmit(action)
  }

  private fun getPlaceholderDrawable(): Drawable? {
    return if (placeholderResId == 0) {
      placeholderDrawable
    } else {
      ContextCompat.getDrawable(picasso.context, placeholderResId)
    }
  }

  /** Create the request optionally passing it through the request transformer.  */
  private fun createRequest(started: Long): Request {
    val id = nextId.getAndIncrement()
    val request = data.build()
    request.id = id
    request.started = started

    val loggingEnabled = picasso.isLoggingEnabled
    if (loggingEnabled) {
      log(OWNER_MAIN, Utils.VERB_CREATED, request.plainId(), request.toString())
    }

    val transformed = picasso.transformRequest(request)
    if (transformed != request) {
      // If the request was changed, copy over the id and timestamp from the original.
      transformed.id = id
      transformed.started = started
      if (loggingEnabled) {
        log(OWNER_MAIN, Utils.VERB_CHANGED, transformed.logId(), "into $transformed")
      }
    }

    return transformed
  }

  private fun performRemoteViewInto(request: Request, action: RemoteViewsAction) {
    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      val bitmap = picasso.quickMemoryCacheCheck(action.request.key)
      if (bitmap != null) {
        action.complete(RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY))
        return
      }
    }

    if (placeholderResId != 0) {
      action.setImageResource(placeholderResId)
    }

    picasso.enqueueAndSubmit(action)
  }

  private companion object {
    private val nextId = AtomicInteger()
  }
}
