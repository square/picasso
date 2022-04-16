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
import android.graphics.Bitmap.Config
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.squareup.picasso3.Dispatcher.Companion.HUNTER_COMPLETE
import com.squareup.picasso3.Dispatcher.Companion.REQUEST_BATCH_RESUME
import com.squareup.picasso3.MemoryPolicy.Companion.shouldReadFromMemoryCache
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget
import com.squareup.picasso3.RequestHandler.Result
import com.squareup.picasso3.Utils.OWNER_MAIN
import com.squareup.picasso3.Utils.VERB_COMPLETED
import com.squareup.picasso3.Utils.VERB_ERRORED
import com.squareup.picasso3.Utils.VERB_RESUMED
import com.squareup.picasso3.Utils.calculateDiskCacheSize
import com.squareup.picasso3.Utils.calculateMemoryCacheSize
import com.squareup.picasso3.Utils.checkMain
import com.squareup.picasso3.Utils.createDefaultCacheDir
import com.squareup.picasso3.Utils.log
import okhttp3.Cache
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService

/**
 * Image downloading, transformation, and caching manager.
 *
 * Use [PicassoProvider.get] for a global singleton instance
 * or construct your own instance with [Picasso.Builder].
 */
@OptIn(ExperimentalStdlibApi::class)
class Picasso internal constructor(
  @get:JvmName("-context") internal val context: Context,
  @get:JvmName("-dispatcher") internal val dispatcher: Dispatcher,
  @get:JvmName("-callFactory") internal val callFactory: Call.Factory,
  private val closeableCache: Cache?,
  @get:JvmName("-cache") internal val cache: PlatformLruCache,
  @get:JvmName("-listener") internal val listener: Listener?,
  requestTransformers: List<RequestTransformer>,
  extraRequestHandlers: List<RequestHandler>,
  eventListeners: List<EventListener>,
  @get:JvmName("-defaultBitmapConfig") internal val defaultBitmapConfig: Config?,
  /** Toggle whether to display debug indicators on images.  */
  var indicatorsEnabled: Boolean,
  /**
   * Toggle whether debug logging is enabled.
   *
   * **WARNING:** Enabling this will result in excessive object allocation. This should be only
   * be used for debugging purposes. Do NOT pass `BuildConfig.DEBUG`.
   */
  @Volatile var isLoggingEnabled: Boolean
) : LifecycleObserver {
  @get:JvmName("-requestTransformers")
  internal val requestTransformers: List<RequestTransformer> = requestTransformers.toList()

  @get:JvmName("-requestHandlers")
  internal val requestHandlers: List<RequestHandler>

  @get:JvmName("-eventListeners")
  internal val eventListeners: List<EventListener> = eventListeners.toList()

  @get:JvmName("-targetToAction")
  internal val targetToAction = mutableMapOf<Any, Action>()

  @get:JvmName("-targetToDeferredRequestCreator")
  internal val targetToDeferredRequestCreator = mutableMapOf<ImageView, DeferredRequestCreator>()

  @get:JvmName("-shutdown")
  @set:JvmName("-shutdown")
  internal var shutdown = false

  init {
    // Adjust this and Builder(Picasso) as internal handlers are added or removed.
    val builtInHandlers = 8

    requestHandlers = buildList(builtInHandlers + extraRequestHandlers.size) {
      // ResourceRequestHandler needs to be the first in the list to avoid
      // forcing other RequestHandlers to perform null checks on request.uri
      // to cover the (request.resourceId != 0) case.
      add(ResourceDrawableRequestHandler.create(context))
      add(ResourceRequestHandler(context))
      addAll(extraRequestHandlers)
      add(ContactsPhotoRequestHandler(context))
      add(MediaStoreRequestHandler(context))
      add(ContentStreamRequestHandler(context))
      add(AssetRequestHandler(context))
      add(FileRequestHandler(context))
      add(NetworkRequestHandler(callFactory))
    }
  }

  @OnLifecycleEvent(ON_DESTROY)
  @JvmName("-cancelAll")
  internal fun cancelAll() {
    checkMain()

    val actions = targetToAction.values.toList()
    for (i in actions.indices) {
      cancelExistingRequest(actions[i].getTarget())
    }

    val deferredRequestCreators = targetToDeferredRequestCreator.values.toList()
    for (i in deferredRequestCreators.indices) {
      deferredRequestCreators[i].cancel()
    }
  }

  /** Cancel any existing requests for the specified target [ImageView]. */
  fun cancelRequest(view: ImageView) {
    // checkMain() is called from cancelExistingRequest()
    cancelExistingRequest(view)
  }

  /** Cancel any existing requests for the specified [BitmapTarget] instance. */
  fun cancelRequest(target: BitmapTarget) {
    // checkMain() is called from cancelExistingRequest()
    cancelExistingRequest(target)
  }

  /** Cancel any existing requests for the specified [DrawableTarget] instance. */
  fun cancelRequest(target: DrawableTarget) {
    // checkMain() is called from cancelExistingRequest()
    cancelExistingRequest(target)
  }

  /**
   * Cancel any existing requests for the specified [RemoteViews] target with the given [viewId].
   */
  fun cancelRequest(remoteViews: RemoteViews, @IdRes viewId: Int) {
    // checkMain() is called from cancelExistingRequest()
    cancelExistingRequest(RemoteViewsTarget(remoteViews, viewId))
  }

  /**
   * Cancel any existing requests with given tag. You can set a tag
   * on new requests with [RequestCreator.tag].
   *
   * @see RequestCreator.tag
   */
  fun cancelTag(tag: Any) {
    checkMain()

    val actions = targetToAction.values.toList()
    for (i in actions.indices) {
      val action = actions[i]
      if (tag == action.tag) {
        cancelExistingRequest(action.getTarget())
      }
    }

    val deferredRequestCreators = targetToDeferredRequestCreator.values.toList()
    for (i in deferredRequestCreators.indices) {
      val deferredRequestCreator = deferredRequestCreators[i]
      if (tag == deferredRequestCreator.tag) {
        deferredRequestCreator.cancel()
      }
    }
  }

  @OnLifecycleEvent(ON_STOP)
  @JvmName("-pauseAll")
  internal fun pauseAll() {
    checkMain()

    val actions = targetToAction.values.toList()
    for (i in actions.indices) {
      dispatcher.dispatchPauseTag(actions[i].tag)
    }

    val deferredRequestCreators = targetToDeferredRequestCreator.values.toList()
    for (i in deferredRequestCreators.indices) {
      val tag = deferredRequestCreators[i].tag
      if (tag != null) {
        dispatcher.dispatchPauseTag(tag)
      }
    }
  }

  /**
   * Pause existing requests with the given tag. Use [resumeTag]
   * to resume requests with the given tag.
   *
   * @see [resumeTag]
   * @see RequestCreator.tag
   */
  fun pauseTag(tag: Any) {
    dispatcher.dispatchPauseTag(tag)
  }

  @OnLifecycleEvent(ON_START)
  @JvmName("-resumeAll")
  internal fun resumeAll() {
    checkMain()

    val actions = targetToAction.values.toList()
    for (i in actions.indices) {
      dispatcher.dispatchResumeTag(actions[i].tag)
    }

    val deferredRequestCreators = targetToDeferredRequestCreator.values.toList()
    for (i in deferredRequestCreators.indices) {
      val tag = deferredRequestCreators[i].tag
      if (tag != null) {
        dispatcher.dispatchResumeTag(tag)
      }
    }
  }

  /**
   * Resume paused requests with the given tag. Use [pauseTag]
   * to pause requests with the given tag.
   *
   * @see [pauseTag]
   * @see RequestCreator.tag
   */
  fun resumeTag(tag: Any) {
    dispatcher.dispatchResumeTag(tag)
  }

  /**
   * Start an image request using the specified URI.
   *
   * Passing `null` as a [uri] will not trigger any request but will set a placeholder,
   * if one is specified.
   *
   * @see #load(File)
   * @see #load(String)
   * @see #load(int)
   */
  fun load(uri: Uri?): RequestCreator {
    return RequestCreator(this, uri, 0)
  }

  /**
   * Start an image request using the specified path. This is a convenience method for calling
   * [load].
   *
   * This path may be a remote URL, file resource (prefixed with `file:`), content resource
   * (prefixed with `content:`), or android resource (prefixed with `android.resource:`.
   *
   * Passing `null` as a [path] will not trigger any request but will set a
   * placeholder, if one is specified.
   *
   * @throws IllegalArgumentException if [path] is empty or blank string.
   * @see #load(Uri)
   * @see #load(File)
   * @see #load(int)
   */
  fun load(path: String?): RequestCreator {
    if (path == null) {
      return RequestCreator(this, null, 0)
    }
    require(path.isNotBlank()) { "Path must not be empty." }
    return load(Uri.parse(path))
  }

  /**
   * Start an image request using the specified image file. This is a convenience method for
   * calling [load].
   *
   * Passing `null` as a [file] will not trigger any request but will set a
   * placeholder, if one is specified.
   *
   * Equivalent to calling [load(Uri.fromFile(file))][load].
   *
   * @see #load(Uri)
   * @see #load(String)
   * @see #load(int)
   */
  fun load(file: File?): RequestCreator {
    return if (file == null) {
      RequestCreator(this, null, 0)
    } else {
      load(Uri.fromFile(file))
    }
  }

  /**
   * Start an image request using the specified drawable resource ID.
   *
   * @see #load(Uri)
   * @see #load(String)
   * @see #load(File)
   */
  fun load(@DrawableRes resourceId: Int): RequestCreator {
    require(resourceId != 0) { "Resource ID must not be zero." }
    return RequestCreator(this, null, resourceId)
  }

  /**
   * Clear all the bitmaps from the memory cache.
   */
  fun evictAll() {
    cache.clear()
  }

  /**
   * Invalidate all memory cached images for the specified [uri].
   *
   * @see #invalidate(String)
   * @see #invalidate(File)
   */
  fun invalidate(uri: Uri?) {
    if (uri != null) {
      cache.clearKeyUri(uri.toString())
    }
  }

  /**
   * Invalidate all memory cached images for the specified [path]. You can also pass a
   * [stable key][RequestCreator.stableKey].
   *
   * @see #invalidate(Uri)
   * @see #invalidate(File)
   */
  fun invalidate(path: String?) {
    if (path != null) {
      invalidate(Uri.parse(path))
    }
  }

  /**
   * Invalidate all memory cached images for the specified [file].
   *
   * @see #invalidate(Uri)
   * @see #invalidate(String)
   */
  fun invalidate(file: File) {
    invalidate(Uri.fromFile(file))
  }

  /** Stops this instance from accepting further requests.  */
  fun shutdown() {
    if (shutdown) {
      return
    }
    cache.clear()

    close()

    dispatcher.shutdown()
    try {
      closeableCache?.close()
    } catch (ignored: IOException) {
    }
    for (deferredRequestCreator in targetToDeferredRequestCreator.values) {
      deferredRequestCreator.cancel()
    }
    targetToDeferredRequestCreator.clear()
    shutdown = true
  }

  @JvmName("-transformRequest")
  internal fun transformRequest(request: Request): Request {
    var nextRequest = request
    for (i in requestTransformers.indices) {
      val transformer = requestTransformers[i]
      nextRequest = transformer.transformRequest(nextRequest)
    }
    return nextRequest
  }

  @JvmName("-defer")
  internal fun defer(view: ImageView, request: DeferredRequestCreator) {
    // If there is already a deferred request, cancel it.
    if (targetToDeferredRequestCreator.containsKey(view)) {
      cancelExistingRequest(view)
    }
    targetToDeferredRequestCreator[view] = request
  }

  @JvmName("-enqueueAndSubmit")
  internal fun enqueueAndSubmit(action: Action) {
    val target = action.getTarget()
    if (targetToAction[target] !== action) {
      // This will also check we are on the main thread.
      cancelExistingRequest(target)
      targetToAction[target] = action
    }
    submit(action)
  }

  @JvmName("-submit")
  internal fun submit(action: Action) {
    dispatcher.dispatchSubmit(action)
  }

  @JvmName("-quickMemoryCacheCheck")
  internal fun quickMemoryCacheCheck(key: String): Bitmap? {
    val cached = cache[key]
    if (cached != null) {
      cacheHit()
    } else {
      cacheMiss()
    }
    return cached
  }

  @JvmName("-complete")
  internal fun complete(hunter: BitmapHunter) {
    val single = hunter.action
    val joined = hunter.actions

    val hasMultiple = !joined.isNullOrEmpty()
    val shouldDeliver = single != null || hasMultiple

    if (!shouldDeliver) {
      return
    }

    val exception = hunter.exception
    val result = hunter.result

    single?.let { deliverAction(result, it, exception) }

    if (joined != null) {
      for (i in joined.indices) {
        deliverAction(result, joined[i], exception)
      }
    }

    if (listener != null && exception != null) {
      listener.onImageLoadFailed(this, hunter.data.uri, exception)
    }
  }

  @JvmName("-resumeAction")
  internal fun resumeAction(action: Action) {
    val bitmap = if (shouldReadFromMemoryCache(action.request.memoryPolicy)) {
      quickMemoryCacheCheck(action.request.key)
    } else null

    if (bitmap != null) {
      // Resumed action is cached, complete immediately.
      deliverAction(Result.Bitmap(bitmap, MEMORY), action, null)
      if (isLoggingEnabled) {
        log(
          owner = OWNER_MAIN,
          verb = VERB_COMPLETED,
          logId = action.request.logId(),
          extras = "from $MEMORY"
        )
      }
    } else {
      // Re-submit the action to the executor.
      enqueueAndSubmit(action)
      if (isLoggingEnabled) {
        log(
          owner = OWNER_MAIN,
          verb = VERB_RESUMED,
          logId = action.request.logId()
        )
      }
    }
  }

  private fun deliverAction(result: Result?, action: Action, e: Exception?) {
    if (action.cancelled) {
      return
    }
    if (!action.willReplay) {
      targetToAction.remove(action.getTarget())
    }
    if (result != null) {
      action.complete(result)
      if (isLoggingEnabled) {
        log(
          owner = OWNER_MAIN,
          verb = VERB_COMPLETED,
          logId = action.request.logId(),
          extras = "from ${result.loadedFrom}"
        )
      }
    } else if (e != null) {
      action.error(e)
      if (isLoggingEnabled) {
        log(
          owner = OWNER_MAIN,
          verb = VERB_ERRORED,
          logId = action.request.logId(),
          extras = e.message
        )
      }
    }
  }

  private fun cancelExistingRequest(target: Any) {
    checkMain()
    val action = targetToAction.remove(target)
    if (action != null) {
      action.cancel()
      dispatcher.dispatchCancel(action)
    }
    if (target is ImageView) {
      val deferredRequestCreator = targetToDeferredRequestCreator.remove(target)
      deferredRequestCreator?.cancel()
    }
  }

  fun newBuilder(): Builder = Builder(this)

  /** Fluent API for creating [Picasso] instances.  */
  class Builder {
    private val context: Context
    private var callFactory: Call.Factory? = null
    private var service: ExecutorService? = null
    private var cache: PlatformLruCache? = null
    private var listener: Listener? = null
    private val requestTransformers = mutableListOf<RequestTransformer>()
    private val requestHandlers = mutableListOf<RequestHandler>()
    private val eventListeners = mutableListOf<EventListener>()
    private var defaultBitmapConfig: Config? = null
    private var indicatorsEnabled = false
    private var loggingEnabled = false

    /** Start building a new [Picasso] instance.  */
    constructor(context: Context) {
      this.context = context.applicationContext
    }

    internal constructor(picasso: Picasso) {
      context = picasso.context
      callFactory = picasso.callFactory
      service = picasso.dispatcher.service
      cache = picasso.cache
      listener = picasso.listener
      requestTransformers += picasso.requestTransformers
      // See Picasso(). Removes internal request handlers added before and after custom handlers.
      val numRequestHandlers = picasso.requestHandlers.size
      requestHandlers += picasso.requestHandlers.subList(2, numRequestHandlers - 6)
      eventListeners += picasso.eventListeners

      defaultBitmapConfig = picasso.defaultBitmapConfig
      indicatorsEnabled = picasso.indicatorsEnabled
      loggingEnabled = picasso.isLoggingEnabled
    }

    /**
     * Specify the default [Bitmap.Config] used when decoding images. This can be overridden
     * on a per-request basis using [RequestCreator.config].
     */
    fun defaultBitmapConfig(bitmapConfig: Config) = apply {
      defaultBitmapConfig = bitmapConfig
    }

    /**
     * Specify the HTTP client to be used for network requests.
     *
     * Note: Calling [callFactory] overwrites this value.
     */
    fun client(client: OkHttpClient) = apply {
      callFactory = client
    }

    /**
     * Specify the call factory to be used for network requests.
     *
     * Note: Calling [client] overwrites this value.
     */
    fun callFactory(factory: Call.Factory) = apply {
      callFactory = factory
    }

    /**
     * Specify the executor service for loading images in the background.
     *
     * Note: Calling [Picasso.shutdown] will not shutdown supplied executors.
     */
    fun executor(executorService: ExecutorService) = apply {
      service = executorService
    }

    /**
     * Specify the memory cache size in bytes to use for the most recent images.
     * A size of 0 disables in-memory caching.
     */
    fun withCacheSize(maxByteCount: Int) = apply {
      require(maxByteCount >= 0) { "maxByteCount < 0: $maxByteCount" }
      cache = PlatformLruCache(maxByteCount)
    }

    /** Specify a listener for interesting events. */
    fun listener(listener: Listener) = apply {
      this.listener = listener
    }

    /** Add a transformer that observes and potentially modify all incoming requests. */
    fun addRequestTransformer(transformer: RequestTransformer) = apply {
      requestTransformers += transformer
    }

    /** Register a [RequestHandler]. */
    fun addRequestHandler(requestHandler: RequestHandler) = apply {
      requestHandlers += requestHandler
    }

    /** Register a [EventListener]. */
    fun addEventListener(eventListener: EventListener) = apply {
      eventListeners += eventListener
    }

    /** Toggle whether to display debug indicators on images. */
    fun indicatorsEnabled(enabled: Boolean) = apply {
      indicatorsEnabled = enabled
    }

    /**
     * Toggle whether debug logging is enabled.
     *
     * **WARNING:** Enabling this will result in excessive object allocation. This should be only
     * be used for debugging purposes. Do NOT pass `BuildConfig.DEBUG`.
     */
    fun loggingEnabled(enabled: Boolean) = apply {
      loggingEnabled = enabled
    }

    /** Create the [Picasso] instance. */
    fun build(): Picasso {
      var unsharedCache: okhttp3.Cache? = null
      if (callFactory == null) {
        val cacheDir = createDefaultCacheDir(context)
        val maxSize = calculateDiskCacheSize(cacheDir)
        unsharedCache = okhttp3.Cache(cacheDir, maxSize)
        callFactory = OkHttpClient.Builder()
          .cache(unsharedCache)
          .build()
      }
      if (cache == null) {
        cache = PlatformLruCache(calculateMemoryCacheSize(context))
      }
      if (service == null) {
        service = PicassoExecutorService()
      }

      val dispatcher = Dispatcher(context, service!!, HANDLER, cache!!)

      return Picasso(
        context, dispatcher, callFactory!!, unsharedCache, cache!!, listener,
        requestTransformers, requestHandlers, eventListeners, defaultBitmapConfig,
        indicatorsEnabled, loggingEnabled
      )
    }
  }

  /** Event listener methods **/

  @JvmName("-cacheMaxSize") // Prefix with '-' to hide from Java.
  internal fun cacheMaxSize(maxSize: Int) {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].cacheMaxSize(maxSize)
    }
  }

  @JvmName("-cacheSize") // Prefix with '-' to hide from Java.
  internal fun cacheSize(size: Int) {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].cacheSize(size)
    }
  }

  @JvmName("-cacheHit") // Prefix with '-' to hide from Java.
  internal fun cacheHit() {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].cacheHit()
    }
  }

  @JvmName("-cacheMiss") // Prefix with '-' to hide from Java.
  internal fun cacheMiss() {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].cacheMiss()
    }
  }

  @JvmName("-downloadFinished") // Prefix with '-' to hide from Java.
  internal fun downloadFinished(size: Long) {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].downloadFinished(size)
    }
  }

  @JvmName("-bitmapDecoded") // Prefix with '-' to hide from Java.
  internal fun bitmapDecoded(bitmap: Bitmap) {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].bitmapDecoded(bitmap)
    }
  }

  @JvmName("-bitmapTransformed") // Prefix with '-' to hide from Java.
  internal fun bitmapTransformed(bitmap: Bitmap) {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].bitmapTransformed(bitmap)
    }
  }

  @JvmName("-close") // Prefix with '-' to hide from Java.
  internal fun close() {
    val numListeners = eventListeners.size
    for (i in 0 until numListeners) {
      eventListeners[i].close()
    }
  }

  /** Callbacks for Picasso events.  */
  fun interface Listener {
    /**
     * Invoked when an image has failed to load. This is useful for reporting image failures to a
     * remote analytics service, for example.
     */
    fun onImageLoadFailed(picasso: Picasso, uri: Uri?, exception: Exception)
  }

  /**
   * A transformer that is called immediately before every request is submitted. This can be used to
   * modify any information about a request.
   *
   * For example, if you use a CDN you can change the hostname for the image based on the current
   * location of the user in order to get faster download speeds.
   */
  fun interface RequestTransformer {
    /**
     * Transform a request before it is submitted to be processed.
     *
     * @return The original request or a new request to replace it. Must not be null.
     */
    fun transformRequest(request: Request): Request
  }

  /**
   * The priority of a request.
   *
   * @see RequestCreator.priority
   */
  enum class Priority {
    LOW,
    NORMAL,
    /**
     * High priority requests will post to the front of main thread's message queue when
     * they complete loading and their images need to be rendered.
     */
    HIGH
  }

  /** Describes where the image was loaded from.  */
  enum class LoadedFrom(@get:JvmName("-debugColor") internal val debugColor: Int) {
    MEMORY(Color.GREEN),
    DISK(Color.BLUE),
    NETWORK(Color.RED);
  }

  internal companion object {
    @get:JvmName("-handler")
    internal val HANDLER: Handler = object : Handler(Looper.getMainLooper()) {
      override fun handleMessage(msg: Message) {
        when (msg.what) {
          HUNTER_COMPLETE -> {
            val hunter = msg.obj as BitmapHunter
            hunter.picasso.complete(hunter)
          }
          REQUEST_BATCH_RESUME -> {
            val batch = msg.obj as List<Action>
            for (i in batch.indices) {
              val action = batch[i]
              action.picasso.resumeAction(action)
            }
          }
          else -> throw AssertionError("Unknown handler message received: " + msg.what)
        }
      }
    }
  }
}

const val TAG = "Picasso"
