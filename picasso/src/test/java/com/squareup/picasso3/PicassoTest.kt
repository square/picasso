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
import android.graphics.Bitmap.Config.ALPHA_8
import android.graphics.Bitmap.Config.ARGB_8888
import android.net.Uri
import android.widget.RemoteViews
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.Picasso.Listener
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget
import com.squareup.picasso3.RequestHandler.Result
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import com.squareup.picasso3.TestUtils.EventRecorder
import com.squareup.picasso3.TestUtils.FakeAction
import com.squareup.picasso3.TestUtils.NO_HANDLERS
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY
import com.squareup.picasso3.TestUtils.URI_1
import com.squareup.picasso3.TestUtils.URI_KEY_1
import com.squareup.picasso3.TestUtils.defaultPicasso
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockAction
import com.squareup.picasso3.TestUtils.mockBitmapTarget
import com.squareup.picasso3.TestUtils.mockDeferredRequestCreator
import com.squareup.picasso3.TestUtils.mockDrawableTarget
import com.squareup.picasso3.TestUtils.mockHunter
import com.squareup.picasso3.TestUtils.mockImageViewTarget
import com.squareup.picasso3.TestUtils.mockPicasso
import com.squareup.picasso3.TestUtils.mockRequestCreator
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations.initMocks
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PicassoTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Mock internal lateinit var context: Context
  @Mock internal lateinit var dispatcher: Dispatcher
  @Mock internal lateinit var requestHandler: RequestHandler
  @Mock internal lateinit var listener: Listener

  private val cache = PlatformLruCache(2048)
  private val eventRecorder = EventRecorder()
  private val bitmap = makeBitmap()

  private lateinit var picasso: Picasso

  @Before fun setUp() {
    initMocks(this)
    picasso = Picasso(
      context = context,
      dispatcher = dispatcher,
      callFactory = UNUSED_CALL_FACTORY,
      closeableCache = null,
      cache = cache,
      listener = listener,
      requestTransformers = NO_TRANSFORMERS,
      extraRequestHandlers = NO_HANDLERS,
      eventListeners = listOf(eventRecorder),
      defaultBitmapConfig = ARGB_8888,
      indicatorsEnabled = false,
      isLoggingEnabled = false
    )
  }

  @Test fun submitWithTargetInvokesDispatcher() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    assertThat(picasso.targetToAction).isEmpty()
    picasso.enqueueAndSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    verify(dispatcher).dispatchSubmit(action)
  }

  @Test fun submitWithSameActionDoesNotCancel() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    picasso.enqueueAndSubmit(action)
    verify(dispatcher).dispatchSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    assertThat(picasso.targetToAction.containsValue(action)).isTrue()
    picasso.enqueueAndSubmit(action)
    assertThat(action.cancelled).isFalse()
    verify(dispatcher, never()).dispatchCancel(action)
  }

  @Test fun quickMemoryCheckReturnsBitmapIfInCache() {
    cache[URI_KEY_1] = bitmap
    val cached = picasso.quickMemoryCacheCheck(URI_KEY_1)
    assertThat(cached).isEqualTo(bitmap)
    assertThat(eventRecorder.cacheHits).isGreaterThan(0)
  }

  @Test fun quickMemoryCheckReturnsNullIfNotInCache() {
    val cached = picasso.quickMemoryCacheCheck(URI_KEY_1)
    assertThat(cached).isNull()
    assertThat(eventRecorder.cacheMisses).isGreaterThan(0)
  }

  @Test fun completeInvokesSuccessOnAllSuccessfulRequests() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action1)
    val action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    hunter.attach(action2)
    action2.cancelled = true

    hunter.run()
    picasso.complete(hunter)

    verifyActionComplete(action1)
    assertThat(action2.completedResult).isNull()
  }

  @Test fun completeInvokesErrorOnAllFailedRequests() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val exception = mock(Exception::class.java)
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action1, exception)
    val action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    hunter.attach(action2)
    action2.cancelled = true
    hunter.run()
    picasso.complete(hunter)

    assertThat(action1.errorException).hasCauseThat().isEqualTo(exception)
    assertThat(action2.errorException).isNull()
    verify(listener).onImageLoadFailed(picasso, URI_1, action1.errorException!!)
  }

  @Test fun completeInvokesErrorOnFailedResourceRequests() {
    val action = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = null,
      resourceId = 123,
      target = mockImageViewTarget()
    )
    val exception = mock(Exception::class.java)
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action, exception)
    hunter.run()
    picasso.complete(hunter)

    assertThat(action.errorException).hasCauseThat().isEqualTo(exception)
    verify(listener).onImageLoadFailed(picasso, null, action.errorException!!)
  }

  @Test fun completeDeliversToSingle() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action)
    hunter.run()
    picasso.complete(hunter)

    verifyActionComplete(action)
  }

  @Test fun completeWithReplayDoesNotRemove() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    action.willReplay = true
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action)
    hunter.run()
    picasso.enqueueAndSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    picasso.complete(hunter)
    assertThat(picasso.targetToAction).hasSize(1)

    verifyActionComplete(action)
  }

  @Test fun completeDeliversToSingleAndMultiple() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action)
    hunter.attach(action2)
    hunter.run()
    picasso.complete(hunter)

    verifyActionComplete(action)
    verifyActionComplete(action2)
  }

  @Test fun completeSkipsIfNoActions() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action)
    hunter.detach(action)
    hunter.run()
    picasso.complete(hunter)

    assertThat(hunter.action).isNull()
    assertThat(hunter.actions).isNull()
  }

  @Test fun resumeActionTriggersSubmitOnPausedAction() {
    val request = Request.Builder(URI_1, 0, ARGB_8888).build()
    val action = object : Action(mockPicasso(RuntimeEnvironment.application), request) {
      override fun complete(result: Result) = fail("Test execution should not call this method")
      override fun error(e: Exception) = fail("Test execution should not call this method")
      override fun getTarget(): Any = this
    }
    picasso.resumeAction(action)
    verify(dispatcher).dispatchSubmit(action)
  }

  @Test fun resumeActionImmediatelyCompletesCachedRequest() {
    cache[URI_KEY_1] = bitmap
    val request = Request.Builder(URI_1, 0, ARGB_8888).build()
    val action = object : Action(mockPicasso(RuntimeEnvironment.application), request) {
      override fun complete(result: Result) {
        assertThat(result).isInstanceOf(Bitmap::class.java)
        val bitmapResult = result as Bitmap
        assertThat(bitmapResult.bitmap).isEqualTo(bitmap)
        assertThat(bitmapResult.loadedFrom).isEqualTo(MEMORY)
      }

      override fun error(e: Exception) =
        fail("Reading from memory cache should not throw an exception")

      override fun getTarget(): Any = this
    }

    picasso.resumeAction(action)
  }

  @Test fun cancelExistingRequestWithUnknownTarget() {
    val target = mockImageViewTarget()
    val action = mockAction(picasso, URI_KEY_1, URI_1, target)
    assertThat(action.cancelled).isFalse()
    picasso.cancelRequest(target)
    assertThat(action.cancelled).isFalse()
    verifyZeroInteractions(dispatcher)
  }

  @Test fun cancelExistingRequestWithImageViewTarget() {
    val target = mockImageViewTarget()
    val action = mockAction(picasso, URI_KEY_1, URI_1, target)
    picasso.enqueueAndSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    assertThat(action.cancelled).isFalse()
    picasso.cancelRequest(target)
    assertThat(picasso.targetToAction).isEmpty()
    assertThat(action.cancelled).isTrue()
    verify(dispatcher).dispatchCancel(action)
  }

  @Test fun cancelExistingRequestWithDeferredImageViewTarget() {
    val target = mockImageViewTarget()
    val creator = mockRequestCreator(picasso)
    val deferredRequestCreator = mockDeferredRequestCreator(creator, target)
    picasso.targetToDeferredRequestCreator[target] = deferredRequestCreator
    picasso.cancelRequest(target)
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator)
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty()
  }

  @Test fun enqueueingDeferredRequestCancelsThePreviousOne() {
    val target = mockImageViewTarget()
    val creator = mockRequestCreator(picasso)
    val firstRequestCreator = mockDeferredRequestCreator(creator, target)
    picasso.defer(target, firstRequestCreator)
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target)

    val secondRequestCreator = mockDeferredRequestCreator(creator, target)
    picasso.defer(target, secondRequestCreator)
    verify(target).removeOnAttachStateChangeListener(firstRequestCreator)
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target)
  }

  @Test fun cancelExistingRequestWithBitmapTarget() {
    val target = mockBitmapTarget()
    val action = mockAction(picasso, URI_KEY_1, URI_1, target)
    picasso.enqueueAndSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    assertThat(action.cancelled).isFalse()
    picasso.cancelRequest(target)
    assertThat(picasso.targetToAction).isEmpty()
    assertThat(action.cancelled).isTrue()
    verify(dispatcher).dispatchCancel(action)
  }

  @Test fun cancelExistingRequestWithDrawableTarget() {
    val target = mockDrawableTarget()
    val action = mockAction(picasso, URI_KEY_1, URI_1, target)
    picasso.enqueueAndSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    assertThat(action.cancelled).isFalse()
    picasso.cancelRequest(target)
    assertThat(picasso.targetToAction).isEmpty()
    assertThat(action.cancelled).isTrue()
    verify(dispatcher).dispatchCancel(action)
  }

  @Test fun cancelExistingRequestWithRemoteViewTarget() {
    val layoutId = 0
    val viewId = 1
    val remoteViews = RemoteViews("com.squareup.picasso3.test", layoutId)
    val target = RemoteViewsTarget(remoteViews, viewId)
    val action = mockAction(picasso, URI_KEY_1, URI_1, target)
    picasso.enqueueAndSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    assertThat(action.cancelled).isFalse()
    picasso.cancelRequest(remoteViews, viewId)
    assertThat(picasso.targetToAction).isEmpty()
    assertThat(action.cancelled).isTrue()
    verify(dispatcher).dispatchCancel(action)
  }

  @Test fun cancelTagAllActions() {
    val target = mockImageViewTarget()
    val action = mockAction(picasso, URI_KEY_1, URI_1, target, tag = "TAG")
    picasso.enqueueAndSubmit(action)
    assertThat(picasso.targetToAction).hasSize(1)
    assertThat(action.cancelled).isFalse()
    picasso.cancelTag("TAG")
    assertThat(picasso.targetToAction).isEmpty()
    assertThat(action.cancelled).isTrue()
  }

  @Test fun cancelTagAllDeferredRequests() {
    val target = mockImageViewTarget()
    val creator = mockRequestCreator(picasso).tag("TAG")
    val deferredRequestCreator = mockDeferredRequestCreator(creator, target)
    picasso.defer(target, deferredRequestCreator)
    picasso.cancelTag("TAG")
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator)
  }

  @Test fun deferAddsToMap() {
    val target = mockImageViewTarget()
    val creator = mockRequestCreator(picasso)
    val deferredRequestCreator = mockDeferredRequestCreator(creator, target)
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty()
    picasso.defer(target, deferredRequestCreator)
    assertThat(picasso.targetToDeferredRequestCreator).hasSize(1)
  }

  @Test fun shutdown() {
    cache["key"] = makeBitmap(1, 1)
    assertThat(cache.size()).isEqualTo(1)
    picasso.shutdown()
    assertThat(cache.size()).isEqualTo(0)
    assertThat(eventRecorder.closed).isTrue()
    verify(dispatcher).shutdown()
    assertThat(picasso.shutdown).isTrue()
  }

  @Test fun shutdownClosesUnsharedCache() {
    val cache = okhttp3.Cache(temporaryFolder.root, 100)
    val picasso = Picasso(
      context, dispatcher, UNUSED_CALL_FACTORY, cache, this.cache, listener,
      NO_TRANSFORMERS, NO_HANDLERS, listOf(eventRecorder),
      defaultBitmapConfig = ARGB_8888, indicatorsEnabled = false, isLoggingEnabled = false
    )
    picasso.shutdown()
    assertThat(cache.isClosed).isTrue()
  }

  @Test fun shutdownTwice() {
    cache["key"] = makeBitmap(1, 1)
    assertThat(cache.size()).isEqualTo(1)
    picasso.shutdown()
    picasso.shutdown()
    assertThat(cache.size()).isEqualTo(0)
    assertThat(eventRecorder.closed).isTrue()
    verify(dispatcher).shutdown()
    assertThat(picasso.shutdown).isTrue()
  }

  @Test fun shutdownClearsDeferredRequests() {
    val target = mockImageViewTarget()
    val creator = mockRequestCreator(picasso)
    val deferredRequestCreator = mockDeferredRequestCreator(creator, target)
    picasso.targetToDeferredRequestCreator[target] = deferredRequestCreator
    picasso.shutdown()
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator)
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty()
  }

  @Test fun loadThrowsWithInvalidInput() {
    try {
      picasso.load("")
      fail("Empty URL should throw exception.")
    } catch (expected: IllegalArgumentException) {
    }
    try {
      picasso.load("      ")
      fail("Empty URL should throw exception.")
    } catch (expected: IllegalArgumentException) {
    }
    try {
      picasso.load(0)
      fail("Zero resourceId should throw exception.")
    } catch (expected: IllegalArgumentException) {
    }
  }

  @Test fun builderInvalidCache() {
    try {
      Picasso.Builder(RuntimeEnvironment.application).withCacheSize(-1)
      fail()
    } catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessageThat().isEqualTo("maxByteCount < 0: -1")
    }
  }

  @Test fun builderWithoutRequestHandler() {
    val picasso = Picasso.Builder(RuntimeEnvironment.application).build()
    assertThat(picasso.requestHandlers).isNotEmpty()
    assertThat(picasso.requestHandlers).doesNotContain(requestHandler)
  }

  @Test fun builderWithRequestHandler() {
    val picasso = Picasso.Builder(RuntimeEnvironment.application)
      .addRequestHandler(requestHandler)
      .build()
    assertThat(picasso.requestHandlers).isNotNull()
    assertThat(picasso.requestHandlers).isNotEmpty()
    assertThat(picasso.requestHandlers).contains(requestHandler)
  }

  @Test fun builderWithDebugIndicators() {
    val picasso = Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build()
    assertThat(picasso.indicatorsEnabled).isTrue()
  }

  @Test fun evictAll() {
    val picasso = Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build()
    picasso.cache["key"] = android.graphics.Bitmap.createBitmap(1, 1, ALPHA_8)
    assertThat(picasso.cache.size()).isEqualTo(1)
    picasso.evictAll()
    assertThat(picasso.cache.size()).isEqualTo(0)
  }

  @Test fun invalidateString() {
    val request = Request.Builder(Uri.parse("https://example.com")).build()
    cache[request.key] = makeBitmap(1, 1)
    assertThat(cache.size()).isEqualTo(1)
    picasso.invalidate("https://example.com")
    assertThat(cache.size()).isEqualTo(0)
  }

  @Test fun invalidateFile() {
    val request = Request.Builder(Uri.fromFile(File("/foo/bar/baz"))).build()
    cache[request.key] = makeBitmap(1, 1)
    assertThat(cache.size()).isEqualTo(1)
    picasso.invalidate(File("/foo/bar/baz"))
    assertThat(cache.size()).isEqualTo(0)
  }

  @Test fun invalidateUri() {
    val request = Request.Builder(URI_1).build()
    cache[request.key] = makeBitmap(1, 1)
    assertThat(cache.size()).isEqualTo(1)
    picasso.invalidate(URI_1)
    assertThat(cache.size()).isEqualTo(0)
  }

  @Test fun clonedRequestHandlersAreIndependent() {
    val original = defaultPicasso(RuntimeEnvironment.application, false, false)

    original.newBuilder()
      .addRequestTransformer(TestUtils.NOOP_TRANSFORMER)
      .addRequestHandler(TestUtils.NOOP_REQUEST_HANDLER)
      .build()

    assertThat(original.requestTransformers).hasSize(NUM_BUILTIN_TRANSFORMERS)
    assertThat(original.requestHandlers).hasSize(NUM_BUILTIN_HANDLERS)
  }

  @Test fun cloneSharesStatefulInstances() {
    val parent = defaultPicasso(RuntimeEnvironment.application, true, true)

    val child = parent.newBuilder().build()

    assertThat(child.context).isEqualTo(parent.context)
    assertThat(child.callFactory).isEqualTo(parent.callFactory)
    assertThat(child.dispatcher.service).isEqualTo(parent.dispatcher.service)
    assertThat(child.cache).isEqualTo(parent.cache)
    assertThat(child.listener).isEqualTo(parent.listener)
    assertThat(child.requestTransformers).isEqualTo(parent.requestTransformers)

    assertThat(child.requestHandlers).hasSize(parent.requestHandlers.size)
    child.requestHandlers.forEachIndexed { index, it ->
      assertThat(it).isInstanceOf(parent.requestHandlers[index].javaClass)
    }

    assertThat(child.defaultBitmapConfig).isEqualTo(parent.defaultBitmapConfig)
    assertThat(child.indicatorsEnabled).isEqualTo(parent.indicatorsEnabled)
    assertThat(child.isLoggingEnabled).isEqualTo(parent.isLoggingEnabled)

    assertThat(child.targetToAction).isEqualTo(parent.targetToAction)
    assertThat(child.targetToDeferredRequestCreator).isEqualTo(
      parent.targetToDeferredRequestCreator
    )
  }

  private fun verifyActionComplete(action: FakeAction) {
    val result = action.completedResult
    assertThat(result).isNotNull()
    assertThat(result).isInstanceOf(RequestHandler.Result.Bitmap::class.java)
    val bitmapResult = result as RequestHandler.Result.Bitmap
    assertThat(bitmapResult.bitmap).isEqualTo(bitmap)
    assertThat(bitmapResult.loadedFrom).isEqualTo(NETWORK)
  }

  companion object {
    private const val NUM_BUILTIN_HANDLERS = 8
    private const val NUM_BUILTIN_TRANSFORMERS = 0
  }
}
