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

import android.app.Notification
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.graphics.Bitmap.Config.ALPHA_8
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.drawable.Drawable
import android.net.NetworkInfo
import android.net.Uri
import android.os.IBinder
import android.provider.ContactsContract.Contacts.CONTENT_URI
import android.provider.ContactsContract.Contacts.Photo
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.util.TypedValue
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.RemoteViews
import com.squareup.picasso3.BitmapHunterTest.TestableBitmapHunter
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Picasso.Priority
import com.squareup.picasso3.Picasso.RequestTransformer
import com.squareup.picasso3.RequestHandler.Result
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import okhttp3.Call
import okhttp3.Response
import okio.Timeout
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal object TestUtils {
  val URI_1: Uri = Uri.parse("http://example.com/1.png")
  val URI_2: Uri = Uri.parse("http://example.com/2.png")
  const val STABLE_1 = "stableExampleKey1"
  val SIMPLE_REQUEST: Request = Request.Builder(URI_1).build()
  val URI_KEY_1: String = SIMPLE_REQUEST.key
  val URI_KEY_2: String = Request.Builder(URI_2).build().key
  val STABLE_URI_KEY_1: String = Request.Builder(URI_1).stableKey(STABLE_1).build().key
  private val FILE_1 = File("C:\\windows\\system32\\logo.exe")
  val FILE_KEY_1: String = Request.Builder(Uri.fromFile(FILE_1)).build().key
  val FILE_1_URL: Uri = Uri.parse("file:///" + FILE_1.path)
  val FILE_1_URL_NO_AUTHORITY: Uri = Uri.parse("file:/" + FILE_1.parent)
  val MEDIA_STORE_CONTENT_1_URL: Uri = Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("1").build()
  val MEDIA_STORE_CONTENT_2_URL: Uri = Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("1").build()
  val MEDIA_STORE_CONTENT_KEY_1: String = Request.Builder(MEDIA_STORE_CONTENT_1_URL).build().key
  val MEDIA_STORE_CONTENT_KEY_2: String = Request.Builder(MEDIA_STORE_CONTENT_2_URL).build().key
  val CONTENT_1_URL: Uri = Uri.parse("content://zip/zap/zoop.jpg")
  val CONTENT_KEY_1: String = Request.Builder(CONTENT_1_URL).build().key
  val CONTACT_URI_1: Uri = CONTENT_URI.buildUpon().appendPath("1234").build()
  val CONTACT_KEY_1: String = Request.Builder(CONTACT_URI_1).build().key
  val CONTACT_PHOTO_URI_1: Uri =
    CONTENT_URI.buildUpon().appendPath("1234").appendPath(Photo.CONTENT_DIRECTORY).build()
  val CONTACT_PHOTO_KEY_1: String = Request.Builder(CONTACT_PHOTO_URI_1).build().key
  const val RESOURCE_ID_1 = 1
  val RESOURCE_ID_KEY_1: String = Request.Builder(RESOURCE_ID_1).build().key
  val ASSET_URI_1: Uri = Uri.parse("file:///android_asset/foo/bar.png")
  val ASSET_KEY_1: String = Request.Builder(ASSET_URI_1).build().key
  private const val RESOURCE_PACKAGE = "com.squareup.picasso3"
  private const val RESOURCE_TYPE = "drawable"
  private const val RESOURCE_NAME = "foo"
  val RESOURCE_ID_URI: Uri = Uri.Builder()
    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
    .authority(RESOURCE_PACKAGE)
    .appendPath(RESOURCE_ID_1.toString())
    .build()
  val RESOURCE_ID_URI_KEY: String = Request.Builder(RESOURCE_ID_URI).build().key
  val RESOURCE_TYPE_URI: Uri = Uri.Builder()
    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
    .authority(RESOURCE_PACKAGE)
    .appendPath(RESOURCE_TYPE)
    .appendPath(RESOURCE_NAME)
    .build()
  val RESOURCE_TYPE_URI_KEY: String = Request.Builder(RESOURCE_TYPE_URI).build().key
  val CUSTOM_URI: Uri = Uri.parse("foo://bar")
  val CUSTOM_URI_KEY: String = Request.Builder(CUSTOM_URI).build().key
  const val BITMAP_RESOURCE_VALUE = "foo.png"
  const val XML_RESOURCE_VALUE = "foo.xml"
  private val DEFAULT_CONFIG = ARGB_8888
  private const val DEFAULT_CACHE_SIZE = 123
  const val CUSTOM_HEADER_NAME = "Cache-Control"
  const val CUSTOM_HEADER_VALUE = "no-cache"

  fun mockPackageResourceContext(): Context {
    val context = mock(Context::class.java)
    val pm = mock(PackageManager::class.java)
    val res = mock(Resources::class.java)

    doReturn(pm).`when`(context).packageManager
    try {
      doReturn(res).`when`(pm).getResourcesForApplication(RESOURCE_PACKAGE)
    } catch (e: NameNotFoundException) {
      throw RuntimeException(e)
    }
    doReturn(RESOURCE_ID_1).`when`(res)
      .getIdentifier(RESOURCE_NAME, RESOURCE_TYPE, RESOURCE_PACKAGE)
    return context
  }

  fun mockResources(resValueString: String): Resources {
    val resources = mock(Resources::class.java)
    doAnswer { invocation: InvocationOnMock ->
      val args = invocation.arguments
      (args[1] as TypedValue).string = resValueString
      null
    }.`when`(resources).getValue(anyInt(), any(TypedValue::class.java), anyBoolean())

    return resources
  }

  fun mockRequest(uri: Uri): Request = Request.Builder(uri).build()

  fun mockAction(
    picasso: Picasso,
    key: String,
    uri: Uri? = null,
    target: Any = mockBitmapTarget(),
    resourceId: Int = 0,
    priority: Priority? = null,
    tag: String? = null,
    headers: Map<String, String> = emptyMap(),
  ): FakeAction {
    val builder = Request.Builder(uri, resourceId, DEFAULT_CONFIG).stableKey(key)
    if (priority != null) {
      builder.priority(priority)
    }
    if (tag != null) {
      builder.tag(tag)
    }
    headers.forEach { (key, value) ->
      builder.addHeader(key, value)
    }
    val request = builder.build()
    return mockAction(picasso, request, target)
  }

  fun mockAction(picasso: Picasso, request: Request, target: Any = mockBitmapTarget()) =
    FakeAction(picasso, request, target)

  fun mockImageViewTarget(): ImageView = mock(ImageView::class.java)

  fun mockRemoteViews(): RemoteViews = mock(RemoteViews::class.java)

  fun mockNotification(): Notification = mock(Notification::class.java)

  fun mockFitImageViewTarget(alive: Boolean): ImageView {
    val observer = mock(ViewTreeObserver::class.java)
    `when`(observer.isAlive).thenReturn(alive)
    val mock = mock(ImageView::class.java)
    `when`(mock.windowToken).thenReturn(mock(IBinder::class.java))
    `when`(mock.viewTreeObserver).thenReturn(observer)
    return mock
  }

  fun mockBitmapTarget(): BitmapTarget = mock(BitmapTarget::class.java)

  fun mockDrawableTarget(): DrawableTarget = mock(DrawableTarget::class.java)

  fun mockCallback(): Callback = mock(Callback::class.java)

  fun mockDeferredRequestCreator(
    creator: RequestCreator?,
    target: ImageView
  ): DeferredRequestCreator {
    val observer = mock(ViewTreeObserver::class.java)
    `when`(target.viewTreeObserver).thenReturn(observer)
    return DeferredRequestCreator(creator!!, target, null)
  }

  fun mockRequestCreator(picasso: Picasso) = RequestCreator(picasso, null, 0)

  fun mockNetworkInfo(isConnected: Boolean = false): NetworkInfo {
    val mock = mock(NetworkInfo::class.java)
    `when`(mock.isConnected).thenReturn(isConnected)
    `when`(mock.isConnectedOrConnecting).thenReturn(isConnected)
    return mock
  }

  fun mockHunter(
    picasso: Picasso,
    result: Result,
    action: Action,
    e: Exception? = null,
    shouldRetry: Boolean = false,
    supportsReplay: Boolean = false,
    dispatcher: Dispatcher = mock(Dispatcher::class.java),
  ): BitmapHunter =
    TestableBitmapHunter(
      picasso = picasso,
      dispatcher = dispatcher,
      cache = PlatformLruCache(0),
      action = action,
      result = (result as Bitmap).bitmap,
      exception = e,
      shouldRetry = shouldRetry,
      supportsReplay = supportsReplay
    )

  fun mockPicasso(context: Context): Picasso {
    // Inject a RequestHandler that can handle any request.
    val requestHandler: RequestHandler = object : RequestHandler() {
      override fun canHandleRequest(data: Request): Boolean {
        return true
      }

      override fun load(picasso: Picasso, request: Request, callback: Callback) {
        val defaultResult = makeBitmap()
        val result = RequestHandler.Result.Bitmap(defaultResult, MEMORY)
        callback.onSuccess(result)
      }
    }

    return mockPicasso(context, requestHandler)
  }

  fun mockPicasso(context: Context, requestHandler: RequestHandler): Picasso {
    return Picasso.Builder(context)
      .callFactory(UNUSED_CALL_FACTORY)
      .withCacheSize(0)
      .addRequestHandler(requestHandler)
      .build()
  }

  fun makeBitmap(
    width: Int = 10,
    height: Int = 10
  ): android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(width, height, ALPHA_8)

  fun makeLoaderWithDrawable(drawable: Drawable?): DrawableLoader = DrawableLoader { drawable }

  internal class FakeAction(
    picasso: Picasso,
    request: Request,
    private val target: Any
  ) : Action(picasso, request) {
    var completedResult: Result? = null
    var errorException: Exception? = null

    override fun complete(result: Result) {
      completedResult = result
    }

    override fun error(e: Exception) {
      errorException = e
    }

    override fun getTarget(): Any = target
  }

  val UNUSED_CALL_FACTORY = Call.Factory { throw AssertionError() }
  val NOOP_REQUEST_HANDLER: RequestHandler = object : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean = false
    override fun load(picasso: Picasso, request: Request, callback: Callback) = Unit
  }
  val NOOP_TRANSFORMER = RequestTransformer { Request.Builder(0).build() }
  private val NOOP_LISTENER = Picasso.Listener { _: Picasso, _: Uri?, _: Exception -> }
  val NO_TRANSFORMERS: List<RequestTransformer> = emptyList()
  val NO_HANDLERS: List<RequestHandler> = emptyList()
  val NO_EVENT_LISTENERS: List<EventListener> = emptyList()

  fun defaultPicasso(
    context: Context,
    hasRequestHandlers: Boolean,
    hasTransformers: Boolean
  ): Picasso {
    val builder = Picasso.Builder(context)

    if (hasRequestHandlers) {
      builder.addRequestHandler(NOOP_REQUEST_HANDLER)
    }
    if (hasTransformers) {
      builder.addRequestTransformer(NOOP_TRANSFORMER)
    }
    return builder
      .callFactory(UNUSED_CALL_FACTORY)
      .defaultBitmapConfig(DEFAULT_CONFIG)
      .executor(PicassoExecutorService())
      .indicatorsEnabled(true)
      .listener(NOOP_LISTENER)
      .loggingEnabled(true)
      .withCacheSize(DEFAULT_CACHE_SIZE)
      .build()
  }

  internal class EventRecorder : EventListener {
    var maxCacheSize = 0
    var cacheSize = 0
    var cacheHits = 0
    var cacheMisses = 0
    var downloadSize: Long = 0
    var decodedBitmap: android.graphics.Bitmap? = null
    var transformedBitmap: android.graphics.Bitmap? = null
    var closed = false

    override fun cacheMaxSize(maxSize: Int) {
      maxCacheSize = maxSize
    }

    override fun cacheSize(size: Int) {
      cacheSize = size
    }

    override fun cacheHit() {
      cacheHits++
    }

    override fun cacheMiss() {
      cacheMisses++
    }

    override fun downloadFinished(size: Long) {
      downloadSize = size
    }

    override fun bitmapDecoded(bitmap: android.graphics.Bitmap) {
      decodedBitmap = bitmap
    }

    override fun bitmapTransformed(bitmap: android.graphics.Bitmap) {
      transformedBitmap = bitmap
    }

    override fun close() {
      closed = true
    }
  }

  internal class PremadeCall(
    private val request: okhttp3.Request,
    private val response: Response
  ) : Call {
    override fun request(): okhttp3.Request = request
    override fun execute(): Response = response
    override fun enqueue(responseCallback: okhttp3.Callback) {
      try {
        responseCallback.onResponse(this, response)
      } catch (e: IOException) {
        throw AssertionError(e)
      }
    }
    override fun cancel(): Unit = throw AssertionError()
    override fun isExecuted(): Boolean = throw AssertionError()
    override fun isCanceled(): Boolean = throw AssertionError()
    override fun clone(): Call = throw AssertionError()
    override fun timeout(): Timeout = throw AssertionError()
  }

  class TestDelegatingService(private val delegate: ExecutorService) : ExecutorService {
    var submissions = 0

    override fun shutdown() = delegate.shutdown()
    override fun shutdownNow(): List<Runnable> = throw AssertionError("Not implemented.")
    override fun isShutdown(): Boolean = delegate.isShutdown
    override fun isTerminated(): Boolean = throw AssertionError("Not implemented.")

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
      delegate.awaitTermination(timeout, unit)

    override fun <T> submit(task: Callable<T>): Future<T> =
      throw AssertionError("Not implemented.")

    override fun <T> submit(task: Runnable, result: T): Future<T> =
      throw AssertionError("Not implemented.")

    override fun submit(task: Runnable): Future<*> {
      submissions++
      return delegate.submit(task)
    }

    override fun <T> invokeAll(tasks: Collection<Callable<T>?>): List<Future<T>> =
      throw AssertionError("Not implemented.")

    override fun <T> invokeAll(
      tasks: Collection<Callable<T>?>,
      timeout: Long,
      unit: TimeUnit
    ): List<Future<T>> = throw AssertionError("Not implemented.")

    override fun <T> invokeAny(tasks: Collection<Callable<T>?>): T =
      throw AssertionError("Not implemented.")

    override fun <T> invokeAny(tasks: Collection<Callable<T>?>, timeout: Long, unit: TimeUnit): T =
      throw AssertionError("Not implemented.")

    override fun execute(command: Runnable) = delegate.execute(command)
  }

  fun <T> any(type: Class<T>): T = Mockito.any(type)

  fun <T : Any> eq(value: T): T = Mockito.eq(value) ?: value

  inline fun <reified T : Any> argumentCaptor(): KArgumentCaptor<T> {
    return KArgumentCaptor(ArgumentCaptor.forClass(T::class.java))
  }

  class KArgumentCaptor<T>(
    private val captor: ArgumentCaptor<T>,
  ) {
    val value: T
      get() = captor.value

    fun capture(): T = captor.capture()
  }
}
