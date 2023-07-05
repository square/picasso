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
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper.getMainLooper
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.MemoryPolicy.NO_STORE
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso3.Request.Builder
import com.squareup.picasso3.TestUtils.TestDelegatingService
import com.squareup.picasso3.TestUtils.URI_1
import com.squareup.picasso3.TestUtils.URI_2
import com.squareup.picasso3.TestUtils.URI_KEY_1
import com.squareup.picasso3.TestUtils.URI_KEY_2
import com.squareup.picasso3.TestUtils.any
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockAction
import com.squareup.picasso3.TestUtils.mockBitmapTarget
import com.squareup.picasso3.TestUtils.mockCallback
import com.squareup.picasso3.TestUtils.mockHunter
import com.squareup.picasso3.TestUtils.mockNetworkInfo
import com.squareup.picasso3.TestUtils.mockPicasso
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.initMocks
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.lang.Exception
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

@RunWith(RobolectricTestRunner::class)
class HandlerDispatcherTest {
  @Mock lateinit var context: Context

  @Mock lateinit var connectivityManager: ConnectivityManager

  @Mock lateinit var serviceMock: ExecutorService

  private lateinit var picasso: Picasso
  private lateinit var dispatcher: HandlerDispatcher

  private val executorService = spy(PicassoExecutorService())
  private val cache = PlatformLruCache(2048)
  private val service = TestDelegatingService(executorService)
  private val bitmap1 = makeBitmap()

  @Before fun setUp() {
    initMocks(this)
    `when`(context.applicationContext).thenReturn(context)
    doReturn(mock(Future::class.java)).`when`(executorService).submit(any(Runnable::class.java))
    picasso = mockPicasso(context)
    dispatcher = createDispatcher(service)
  }

  @Test fun shutdownStopsService() {
    val service = PicassoExecutorService()
    dispatcher = createDispatcher(service)
    dispatcher.shutdown()
    assertThat(service.isShutdown).isEqualTo(true)
  }

  @Test fun shutdownUnregistersReceiver() {
    dispatcher.shutdown()
    shadowOf(getMainLooper()).idle()
    verify(context).unregisterReceiver(dispatcher.receiver)
  }

  @Test fun performSubmitWithNewRequestQueuesHunter() {
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    dispatcher.performSubmit(action)
    assertThat(dispatcher.hunterMap).hasSize(1)
    assertThat(service.submissions).isEqualTo(1)
  }

  @Test fun performSubmitWithTwoDifferentRequestsQueuesHunters() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1)
    val action2 = mockAction(picasso, URI_KEY_2, URI_2)
    dispatcher.performSubmit(action1)
    dispatcher.performSubmit(action2)
    assertThat(dispatcher.hunterMap).hasSize(2)
    assertThat(service.submissions).isEqualTo(2)
  }

  @Test fun performSubmitWithExistingRequestAttachesToHunter() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1)
    val action2 = mockAction(picasso, URI_KEY_1, URI_1)
    dispatcher.performSubmit(action1)
    assertThat(dispatcher.hunterMap).hasSize(1)
    assertThat(service.submissions).isEqualTo(1)
    dispatcher.performSubmit(action2)
    assertThat(dispatcher.hunterMap).hasSize(1)
    assertThat(service.submissions).isEqualTo(1)
  }

  @Test fun performSubmitWithShutdownServiceIgnoresRequest() {
    service.shutdown()
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    dispatcher.performSubmit(action)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(service.submissions).isEqualTo(0)
  }

  @Test fun performSubmitWithFetchAction() {
    val pausedTag = "pausedTag"
    dispatcher.pausedTags.add(pausedTag)
    assertThat(dispatcher.pausedActions).isEmpty()

    val fetchAction1 = FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null)
    val fetchAction2 = FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null)
    dispatcher.performSubmit(fetchAction1)
    dispatcher.performSubmit(fetchAction2)

    assertThat(dispatcher.pausedActions).hasSize(2)
  }

  @Test fun performCancelWithFetchActionWithCallback() {
    val pausedTag = "pausedTag"
    dispatcher.pausedTags.add(pausedTag)
    assertThat(dispatcher.pausedActions).isEmpty()
    val callback = mockCallback()

    val fetchAction1 = FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), callback)
    dispatcher.performCancel(fetchAction1)
    fetchAction1.cancel()
    assertThat(dispatcher.pausedActions).isEmpty()
  }

  @Test fun performCancelDetachesRequestAndCleansUp() {
    val target = mockBitmapTarget()
    val action = mockAction(picasso, URI_KEY_1, URI_1, target)
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    hunter.future = FutureTask(mock(Runnable::class.java), mock(Any::class.java))
    dispatcher.hunterMap[URI_KEY_1 + Request.KEY_SEPARATOR] = hunter
    dispatcher.failedActions[target] = action
    dispatcher.performCancel(action)
    assertThat(hunter.action).isNull()
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun performCancelMultipleRequestsDetachesOnly() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1)
    val action2 = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1)
    hunter.attach(action2)
    dispatcher.hunterMap[URI_KEY_1 + Request.KEY_SEPARATOR] = hunter
    dispatcher.performCancel(action1)
    assertThat(hunter.action).isNull()
    assertThat(hunter.actions).containsExactly(action2)
    assertThat(dispatcher.hunterMap).hasSize(1)
  }

  @Test fun performCancelUnqueuesAndDetachesPausedRequest() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), tag = "tag")
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    dispatcher.hunterMap[URI_KEY_1 + Request.KEY_SEPARATOR] = hunter
    dispatcher.pausedTags.add("tag")
    dispatcher.pausedActions[action.getTarget()] = action
    dispatcher.performCancel(action)
    assertThat(hunter.action).isNull()
    assertThat(dispatcher.pausedTags).containsExactly("tag")
    assertThat(dispatcher.pausedActions).isEmpty()
  }

  @Test fun performCompleteSetsResultInCache() {
    val data = Request.Builder(URI_1).build()
    val action = noopAction(data)
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    hunter.run()
    assertThat(cache.size()).isEqualTo(0)

    dispatcher.performComplete(hunter)

    assertThat(hunter.result).isInstanceOf(RequestHandler.Result.Bitmap::class.java)
    val result = hunter.result as RequestHandler.Result.Bitmap
    assertThat(result.bitmap).isEqualTo(bitmap1)
    assertThat(result.loadedFrom).isEqualTo(NETWORK)
    assertThat(cache[hunter.key]).isSameInstanceAs(bitmap1)
  }

  @Test fun performCompleteWithNoStoreMemoryPolicy() {
    val data = Request.Builder(URI_1).memoryPolicy(NO_STORE).build()
    val action = noopAction(data)
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    hunter.run()
    assertThat(cache.size()).isEqualTo(0)

    dispatcher.performComplete(hunter)

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(cache.size()).isEqualTo(0)
  }

  @Test fun performCompleteCleansUpAndPostsToMain() {
    val data = Request.Builder(URI_1).build()
    var completed = false
    val action = noopAction(data, onComplete = { completed = true })
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    hunter.run()

    dispatcher.performComplete(hunter)
    ShadowLooper.idleMainLooper()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(completed).isTrue()
  }

  @Test fun performCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    val data = Request.Builder(URI_1).build()
    var completed = false
    val action = noopAction(data, onComplete = { completed = true })
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    hunter.run()
    hunter.future = FutureTask<Any>(mock(Runnable::class.java), null)
    hunter.future!!.cancel(false)

    dispatcher.performComplete(hunter)
    ShadowLooper.idleMainLooper()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(completed).isFalse()
  }

  @Test fun performErrorCleansUpAndPostsToMain() {
    val exception = RuntimeException()
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), tag = "tag")
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, exception)
    dispatcher.hunterMap[hunter.key] = hunter
    hunter.run()

    dispatcher.performError(hunter)
    ShadowLooper.idleMainLooper()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(action.errorException).isSameInstanceAs(exception)
  }

  @Test fun performErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    val exception = RuntimeException()
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), tag = "tag")
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, exception)
    hunter.future = FutureTask(mock(Runnable::class.java), mock(Any::class.java))
    hunter.future!!.cancel(false)
    dispatcher.hunterMap[hunter.key] = hunter
    hunter.run()

    dispatcher.performError(hunter)
    ShadowLooper.idleMainLooper()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(action.errorException).isNull()
    assertThat(action.completedResult).isNull()
  }

  @Test fun performRetrySkipsIfHunterIsCancelled() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), tag = "tag")
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    hunter.future = FutureTask(mock(Runnable::class.java), mock(Any::class.java))
    hunter.future!!.cancel(false)
    dispatcher.performRetry(hunter)
    assertThat(hunter.isCancelled).isTrue()
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun performRetryForContentLengthResetsNetworkPolicy() {
    val networkInfo = mockNetworkInfo(true)
    `when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
    val action = mockAction(picasso, URI_KEY_2, URI_2)
    val e = ContentLengthException("304 error")
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, e, true)
    hunter.run()
    dispatcher.performRetry(hunter)
    assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse()
  }

  @Test fun performRetryDoesNotMarkForReplayIfNotSupported() {
    val networkInfo = mockNetworkInfo(true)
    val hunter = mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1)
    )
    `when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
    dispatcher.performRetry(hunter)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
    assertThat(service.submissions).isEqualTo(0)
  }

  @Test fun performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    val hunter = mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1),
      e = null,
      shouldRetry = false,
      supportsReplay = true
    )
    val dispatcher = createDispatcher(false)
    dispatcher.performRetry(hunter)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
    assertThat(service.submissions).isEqualTo(0)
  }

  @Test fun performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    val networkInfo = mockNetworkInfo(true)
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget())
    val hunter = mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MEMORY),
      action,
      e = null,
      shouldRetry = false,
      supportsReplay = true
    )
    `when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
    dispatcher.performRetry(hunter)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).hasSize(1)
    assertThat(service.submissions).isEqualTo(0)
  }

  @Test fun performRetryRetriesIfNoNetworkScanning() {
    val hunter = mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1),
      e = null,
      shouldRetry = true
    )
    val dispatcher = createDispatcher(false)
    dispatcher.performRetry(hunter)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
    assertThat(service.submissions).isEqualTo(1)
  }

  @Test fun performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget())
    val hunter = mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MEMORY),
      action,
      e = null,
      shouldRetry = false,
      supportsReplay = true
    )
    dispatcher.performRetry(hunter)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).hasSize(1)
    assertThat(service.submissions).isEqualTo(0)
  }

  @Test fun performRetryRetriesIfShouldRetry() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget())
    val hunter = mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MEMORY),
      action,
      e = null,
      shouldRetry = true
    )
    dispatcher.performRetry(hunter)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
    assertThat(service.submissions).isEqualTo(1)
  }

  @Test fun performRetrySkipIfServiceShutdown() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget())
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    service.shutdown()
    dispatcher.performRetry(hunter)
    assertThat(service.submissions).isEqualTo(0)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun performAirplaneModeChange() {
    assertThat(dispatcher.airplaneMode).isFalse()
    dispatcher.performAirplaneModeChange(true)
    assertThat(dispatcher.airplaneMode).isTrue()
    dispatcher.performAirplaneModeChange(false)
    assertThat(dispatcher.airplaneMode).isFalse()
  }

  @Test fun performNetworkStateChangeWithNullInfoIgnores() {
    val dispatcher = createDispatcher(serviceMock)
    dispatcher.performNetworkStateChange(null)
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun performNetworkStateChangeWithDisconnectedInfoIgnores() {
    val dispatcher = createDispatcher(serviceMock)
    val info = mockNetworkInfo()
    `when`(info.isConnectedOrConnecting).thenReturn(false)
    dispatcher.performNetworkStateChange(info)
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    val dispatcher = createDispatcher(serviceMock)
    val info = mockNetworkInfo(true)
    dispatcher.performNetworkStateChange(info)
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun performPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.performPauseTag("tag")
    assertThat(dispatcher.pausedTags).containsExactly("tag")
    dispatcher.performResumeTag("tag")
    assertThat(dispatcher.pausedTags).isEmpty()
  }

  @Test fun performPauseTagIsIdempotent() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), tag = "tag")
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    dispatcher.hunterMap[URI_KEY_1] = hunter
    assertThat(dispatcher.pausedActions).isEmpty()
    dispatcher.performPauseTag("tag")
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action)
    dispatcher.performPauseTag("tag")
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action)
  }

  @Test fun performPauseTagQueuesNewRequestDoesNotSubmit() {
    dispatcher.performPauseTag("tag")
    val action = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, tag = "tag")
    dispatcher.performSubmit(action)
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.pausedActions).hasSize(1)
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue()
    assertThat(service.submissions).isEqualTo(0)
  }

  @Test fun performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag")
    val action = mockAction(picasso, URI_KEY_1, URI_1, "anothertag")
    dispatcher.performSubmit(action)
    assertThat(dispatcher.hunterMap).hasSize(1)
    assertThat(dispatcher.pausedActions).isEmpty()
    assertThat(service.submissions).isEqualTo(1)
  }

  @Test fun performPauseDetachesRequestAndCancelsHunter() {
    val action = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      tag = "tag"
    )
    val hunter = mockHunter(
      picasso = picasso,
      result = RequestHandler.Result.Bitmap(bitmap1, MEMORY),
      action = action,
      dispatcher = dispatcher
    )
    hunter.future = FutureTask(mock(Runnable::class.java), mock(Any::class.java))
    dispatcher.hunterMap[URI_KEY_1] = hunter
    dispatcher.performPauseTag("tag")
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.pausedActions).hasSize(1)
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue()
    assertThat(hunter.action).isNull()
  }

  @Test fun performPauseOnlyDetachesPausedRequest() {
    val action1 = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      target = mockBitmapTarget(),
      tag = "tag1"
    )
    val action2 = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      target = mockBitmapTarget(),
      tag = "tag2"
    )
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1)
    hunter.attach(action2)
    dispatcher.hunterMap[URI_KEY_1] = hunter
    dispatcher.performPauseTag("tag1")
    assertThat(dispatcher.hunterMap).hasSize(1)
    assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue()
    assertThat(dispatcher.pausedActions).hasSize(1)
    assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue()
    assertThat(hunter.action).isNull()
    assertThat(hunter.actions).containsExactly(action2)
  }

  @Test fun performResumeTagResumesPausedActions() {
    val action = noopAction(Builder(URI_1).tag("tag").build())
    val hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action)
    dispatcher.hunterMap[URI_KEY_1] = hunter
    assertThat(dispatcher.pausedActions).isEmpty()
    dispatcher.performPauseTag("tag")
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action)

    dispatcher.performResumeTag("tag")

    assertThat(dispatcher.pausedActions).isEmpty()
  }

  @Test fun performNetworkStateChangeFlushesFailedHunters() {
    val info = mockNetworkInfo(true)
    val failedAction1 = mockAction(picasso, URI_KEY_1, URI_1)
    val failedAction2 = mockAction(picasso, URI_KEY_2, URI_2)
    dispatcher.failedActions[URI_KEY_1] = failedAction1
    dispatcher.failedActions[URI_KEY_2] = failedAction2
    dispatcher.performNetworkStateChange(info)
    assertThat(service.submissions).isEqualTo(2)
    assertThat(dispatcher.failedActions).isEmpty()
  }

  private fun createDispatcher(scansNetworkChanges: Boolean): HandlerDispatcher {
    return createDispatcher(service, scansNetworkChanges)
  }

  private fun createDispatcher(
    service: ExecutorService,
    scansNetworkChanges: Boolean = true
  ): HandlerDispatcher {
    `when`(connectivityManager.activeNetworkInfo).thenReturn(
      if (scansNetworkChanges) mock(NetworkInfo::class.java) else null
    )
    `when`(context.getSystemService(CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
    `when`(context.checkCallingOrSelfPermission(anyString())).thenReturn(
      if (scansNetworkChanges) PERMISSION_GRANTED else PERMISSION_DENIED
    )
    return HandlerDispatcher(context, service, Handler(getMainLooper()), cache)
  }

  private fun noopAction(data: Request, onComplete: () -> Unit = { }): Action {
    return object : Action(picasso, data) {
      override fun complete(result: RequestHandler.Result) = onComplete()
      override fun error(e: Exception) = Unit
      override fun getTarget(): Any = this
    }
  }
}
