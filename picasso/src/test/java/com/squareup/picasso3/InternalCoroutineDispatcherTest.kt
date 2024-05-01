/*
 * Copyright (C) 2023 Square, Inc.
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
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.MemoryPolicy.NO_STORE
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso3.Picasso.Priority.HIGH
import com.squareup.picasso3.Request.Builder
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.lang.Exception
import java.lang.RuntimeException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

@RunWith(RobolectricTestRunner::class)
class InternalCoroutineDispatcherTest {

  @Mock lateinit var context: Context

  @Mock lateinit var connectivityManager: ConnectivityManager

  private lateinit var picasso: Picasso
  private lateinit var dispatcher: InternalCoroutineDispatcher
  private lateinit var testDispatcher: TestDispatcher

  private val cache = PlatformLruCache(2048)
  private val bitmap1 = TestUtils.makeBitmap()

  @Before fun setUp() {
    MockitoAnnotations.initMocks(this)
    Mockito.`when`(context.applicationContext).thenReturn(context)
    dispatcher = createDispatcher()
  }

  @Test fun shutdownCancelsRunningJob() {
    createDispatcher(true)
    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    dispatcher.dispatchSubmit(action)

    dispatcher.shutdown()
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.isShutdown()).isEqualTo(true)
    assertThat(action.completedResult).isNull()
  }

  @Test fun shutdownPreventsFurtherChannelUse() {
    val dispatcher = createDispatcher(true, backgroundContext = Dispatchers.IO)
    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    dispatcher.shutdown()

    dispatcher.dispatchSubmit(action)

    assertThat(dispatcher.isShutdown()).isEqualTo(true)
    assertThat(action.completedResult).isNull()
  }

  @Test fun shutdownUnregistersReceiver() {
    dispatcher.shutdown()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    Mockito.verify(context).unregisterReceiver(dispatcher.receiver)
  }

  @Test fun dispatchSubmitWithNewRequestQueuesHunter() {
    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    dispatcher.dispatchSubmit(action)

    testDispatcher.scheduler.runCurrent()

    assertThat(action.completedResult).isNotNull()
  }

  @Test fun dispatchSubmitWithTwoDifferentRequestsQueuesHunters() {
    val action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    val action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2)

    dispatcher.dispatchSubmit(action1)
    dispatcher.dispatchSubmit(action2)

    testDispatcher.scheduler.runCurrent()

    assertThat(action1.completedResult).isNotNull()
    assertThat(action2.completedResult).isNotNull()
    assertThat(action2.completedResult).isNotEqualTo(action1.completedResult)
  }

  @Test fun performSubmitWithExistingRequestAttachesToHunter() {
    val action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    val action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)

    dispatcher.dispatchSubmit(action1)
    dispatcher.dispatchSubmit(action2)
    testDispatcher.scheduler.runCurrent()

    assertThat(action1.completedResult).isNotNull()
    assertThat(action2.completedResult).isEqualTo(action1.completedResult)
  }

  @Test fun dispatchSubmitWithShutdownServiceIgnoresRequest() {
    dispatcher.shutdown()

    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    dispatcher.dispatchSubmit(action)
    testDispatcher.scheduler.runCurrent()

    assertThat(action.completedResult).isNull()
  }

  @Test fun dispatchSubmitWithFetchAction() {
    val pausedTag = "pausedTag"
    dispatcher.dispatchPauseTag(pausedTag)
    testDispatcher.scheduler.runCurrent()
    assertThat(dispatcher.pausedActions).isEmpty()

    var completed = false
    val fetchAction1 = noopAction(Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), { completed = true })
    val fetchAction2 = noopAction(Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), { completed = true })
    dispatcher.dispatchSubmit(fetchAction1)
    dispatcher.dispatchSubmit(fetchAction2)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.pausedActions).hasSize(2)
    assertThat(completed).isFalse()
  }

  @Test fun dispatchCancelWithFetchActionWithCallback() {
    val pausedTag = "pausedTag"
    dispatcher.dispatchPauseTag(pausedTag)
    testDispatcher.scheduler.runCurrent()
    assertThat(dispatcher.pausedActions).isEmpty()

    val callback = TestUtils.mockCallback()

    val fetchAction1 = FetchAction(picasso, Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), callback)
    dispatcher.dispatchSubmit(fetchAction1)
    testDispatcher.scheduler.runCurrent()
    assertThat(dispatcher.pausedActions).hasSize(1)

    dispatcher.dispatchCancel(fetchAction1)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.pausedActions).isEmpty()
  }

  @Test fun dispatchCancelDetachesRequestAndCleansUp() {
    val target = TestUtils.mockBitmapTarget()
    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, target)
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action).apply {
      job = Job()
    }
    dispatcher.hunterMap[TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR] = hunter
    dispatcher.failedActions[target] = action

    dispatcher.dispatchCancel(action)
    testDispatcher.scheduler.runCurrent()

    assertThat(hunter.job!!.isCancelled).isTrue()
    assertThat(hunter.action).isNull()
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun dispatchCancelMultipleRequestsDetachesOnly() {
    val action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    val action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action1)
    hunter.attach(action2)
    dispatcher.hunterMap[TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR] = hunter

    dispatcher.dispatchCancel(action1)
    testDispatcher.scheduler.runCurrent()

    assertThat(hunter.action).isNull()
    assertThat(hunter.actions).containsExactly(action2)
    assertThat(dispatcher.hunterMap).hasSize(1)
  }

  @Test fun dispatchCancelUnqueuesAndDetachesPausedRequest() {
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget(),
      tag = "tag"
    )
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)
    dispatcher.dispatchSubmit(action)
    dispatcher.dispatchPauseTag("tag")
    testDispatcher.scheduler.runCurrent()
    dispatcher.hunterMap[TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR] = hunter

    dispatcher.dispatchCancel(action)
    testDispatcher.scheduler.runCurrent()

    assertThat(hunter.action).isNull()
    assertThat(dispatcher.pausedTags).containsExactly("tag")
    assertThat(dispatcher.pausedActions).isEmpty()
  }

  @Test fun dispatchCompleteSetsResultInCache() {
    val data = Request.Builder(TestUtils.URI_1).build()
    val action = noopAction(data)
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)
    hunter.run()
    assertThat(cache.size()).isEqualTo(0)

    dispatcher.dispatchComplete(hunter)
    testDispatcher.scheduler.runCurrent()

    val result = hunter.result as Bitmap
    assertThat(result.bitmap).isEqualTo(bitmap1)
    assertThat(result.loadedFrom).isEqualTo(NETWORK)
    assertThat(cache[hunter.key]).isSameInstanceAs(bitmap1)
  }

  @Test fun dispatchCompleteWithNoStoreMemoryPolicy() {
    val data = Request.Builder(TestUtils.URI_1).memoryPolicy(NO_STORE).build()
    val action = noopAction(data)
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)
    hunter.run()
    assertThat(cache.size()).isEqualTo(0)

    dispatcher.dispatchComplete(hunter)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(cache.size()).isEqualTo(0)
  }

  @Test fun dispatchCompleteCleansUpAndPostsToMain() {
    val data = Request.Builder(TestUtils.URI_1).build()
    var completed = false
    val action = noopAction(data, onComplete = { completed = true })
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)
    hunter.run()

    dispatcher.dispatchComplete(hunter)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(completed).isTrue()
  }

  @Test fun dispatchCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    val data = Request.Builder(TestUtils.URI_1).build()
    var completed = false
    val action = noopAction(data, onComplete = { completed = true })
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)
    hunter.run()
    hunter.job = Job().apply { cancel() }

    dispatcher.dispatchComplete(hunter)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(completed).isFalse()
  }

  @Test fun dispatchErrorCleansUpAndPostsToMain() {
    val exception = RuntimeException()
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget(),
      tag = "tag"
    )
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action, exception)
    hunter.run()
    dispatcher.hunterMap[hunter.key] = hunter

    dispatcher.dispatchFailed(hunter)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(action.errorException).isEqualTo(exception)
  }

  @Test fun dispatchErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    val exception = RuntimeException()
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget(),
      tag = "tag"
    )
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action, exception)
    hunter.run()
    hunter.job = Job().apply { cancel() }
    dispatcher.hunterMap[hunter.key] = hunter

    dispatcher.dispatchFailed(hunter)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(action.errorException).isNull()
  }

  @Test fun dispatchRetrySkipsIfHunterIsCancelled() {
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget(),
      tag = "tag"
    )
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)
    hunter.job = Job().apply { cancel() }

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.runCurrent()

    assertThat(hunter.isCancelled).isTrue()
    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun dispatchRetryForContentLengthResetsNetworkPolicy() {
    val networkInfo = TestUtils.mockNetworkInfo(true)
    Mockito.`when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2)
    val e = ContentLengthException("304 error")
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action, e, true)
    hunter.run()

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse()
  }

  @Test fun dispatchRetryDoesNotMarkForReplayIfNotSupported() {
    val networkInfo = TestUtils.mockNetworkInfo(true)
    val hunter = TestUtils.mockHunter(
      picasso,
      Bitmap(bitmap1, MEMORY),
      TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    )
    Mockito.`when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun dispatchRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    val hunter = TestUtils.mockHunter(
      picasso,
      Bitmap(bitmap1, MEMORY),
      TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1),
      e = null,
      shouldRetry = false,
      supportsReplay = true
    )
    val dispatcher = createDispatcher(false)

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun dispatchRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    val networkInfo = TestUtils.mockNetworkInfo(true)
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget()
    )
    val hunter = TestUtils.mockHunter(
      picasso,
      Bitmap(bitmap1, MEMORY),
      action,
      e = null,
      shouldRetry = false,
      supportsReplay = true
    )
    Mockito.`when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).hasSize(1)
    assertThat(action.willReplay).isTrue()
  }

  @Test fun dispatchRetryRetriesIfNoNetworkScanning() {
    val dispatcher = createDispatcher(false)
    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    val hunter = TestUtils.mockHunter(
      picasso,
      Bitmap(bitmap1, MEMORY),
      action,
      e = null,
      shouldRetry = true,
      dispatcher = dispatcher
    )

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
    assertThat(action.completedResult).isInstanceOf(Bitmap::class.java)
  }

  @Test fun dispatchRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget()
    )
    val hunter = TestUtils.mockHunter(
      picasso,
      Bitmap(bitmap1, MEMORY),
      action,
      e = null,
      shouldRetry = false,
      supportsReplay = true
    )

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).hasSize(1)
    assertThat(action.willReplay).isTrue()
  }

  @Test fun dispatchRetryRetriesIfShouldRetry() {
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget()
    )
    val hunter = TestUtils.mockHunter(
      picasso,
      Bitmap(bitmap1, MEMORY),
      action,
      e = null,
      shouldRetry = true,
      dispatcher = dispatcher
    )

    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
    assertThat(action.completedResult).isInstanceOf(Bitmap::class.java)
  }

  @Test fun dispatchRetrySkipIfServiceShutdown() {
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget()
    )
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)

    dispatcher.shutdown()
    dispatcher.dispatchRetry(hunter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.failedActions).isEmpty()
    assertThat(action.completedResult).isNull()
  }

  @Test fun dispatchAirplaneModeChange() {
    assertThat(dispatcher.airplaneMode).isFalse()

    dispatcher.dispatchAirplaneModeChange(true)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.airplaneMode).isTrue()

    dispatcher.dispatchAirplaneModeChange(false)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.airplaneMode).isFalse()
  }

  @Test fun dispatchNetworkStateChangeWithDisconnectedInfoIgnores() {
    val info = TestUtils.mockNetworkInfo()
    Mockito.`when`(info.isConnectedOrConnecting).thenReturn(false)

    dispatcher.dispatchNetworkStateChange(info)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun dispatchNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    val info = TestUtils.mockNetworkInfo(true)

    dispatcher.dispatchNetworkStateChange(info)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun dispatchPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.dispatchPauseTag("tag")
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.pausedTags).containsExactly("tag")

    dispatcher.dispatchResumeTag("tag")
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.pausedTags).isEmpty()
  }

  @Test fun dispatchPauseTagIsIdempotent() {
    val action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget(),
      tag = "tag"
    )
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action)
    dispatcher.hunterMap[TestUtils.URI_KEY_1] = hunter
    assertThat(dispatcher.pausedActions).isEmpty()

    dispatcher.dispatchPauseTag("tag")
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action)

    dispatcher.dispatchPauseTag("tag")
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action)
  }

  @Test fun dispatchPauseTagQueuesNewRequestDoesNotComplete() {
    dispatcher.dispatchPauseTag("tag")
    val action = TestUtils.mockAction(
      picasso = picasso,
      key = TestUtils.URI_KEY_1,
      uri = TestUtils.URI_1,
      tag = "tag"
    )

    dispatcher.dispatchSubmit(action)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.pausedActions).hasSize(1)
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue()
    assertThat(action.completedResult).isNull()
  }

  @Test fun dispatchPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.dispatchPauseTag("tag")
    testDispatcher.scheduler.runCurrent()

    val action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, "anothertag")
    dispatcher.dispatchSubmit(action)
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.pausedActions).isEmpty()
    assertThat(action.completedResult).isNotNull()
  }

  @Test fun dispatchPauseDetachesRequestAndCancelsHunter() {
    val action = TestUtils.mockAction(
      picasso = picasso,
      key = TestUtils.URI_KEY_1,
      uri = TestUtils.URI_1,
      tag = "tag"
    )
    val hunter = TestUtils.mockHunter(
      picasso = picasso,
      result = Bitmap(bitmap1, MEMORY),
      action = action,
      dispatcher = dispatcher
    )
    hunter.job = Job()

    dispatcher.hunterMap[TestUtils.URI_KEY_1] = hunter
    dispatcher.dispatchPauseTag("tag")
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).isEmpty()
    assertThat(dispatcher.pausedActions).hasSize(1)
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue()
    assertThat(hunter.action).isNull()
    assertThat(action.completedResult).isNull()
  }

  @Test fun dispatchPauseOnlyDetachesPausedRequest() {
    val action1 = TestUtils.mockAction(
      picasso = picasso,
      key = TestUtils.URI_KEY_1,
      uri = TestUtils.URI_1,
      target = TestUtils.mockBitmapTarget(),
      tag = "tag1"
    )
    val action2 = TestUtils.mockAction(
      picasso = picasso,
      key = TestUtils.URI_KEY_1,
      uri = TestUtils.URI_1,
      target = TestUtils.mockBitmapTarget(),
      tag = "tag2"
    )
    val hunter = TestUtils.mockHunter(picasso, Bitmap(bitmap1, MEMORY), action1)
    hunter.attach(action2)
    dispatcher.hunterMap[TestUtils.URI_KEY_1] = hunter

    dispatcher.dispatchPauseTag("tag1")
    testDispatcher.scheduler.runCurrent()

    assertThat(dispatcher.hunterMap).hasSize(1)
    assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue()
    assertThat(dispatcher.pausedActions).hasSize(1)
    assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue()
    assertThat(hunter.action).isNull()
    assertThat(hunter.actions).containsExactly(action2)
  }

  @Test fun dispatchResumeTagIsIdempotent() {
    var completedCount = 0
    val action = noopAction(Builder(TestUtils.URI_1).tag("tag").build(), { completedCount++ })

    dispatcher.dispatchPauseTag("tag")
    dispatcher.dispatchSubmit(action)
    dispatcher.dispatchResumeTag("tag")
    dispatcher.dispatchResumeTag("tag")
    testDispatcher.scheduler.runCurrent()

    assertThat(completedCount).isEqualTo(1)
  }

  @Test fun dispatchNetworkStateChangeFlushesFailedHunters() {
    val info = TestUtils.mockNetworkInfo(true)
    val failedAction1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    val failedAction2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2)
    dispatcher.failedActions[TestUtils.URI_KEY_1] = failedAction1
    dispatcher.failedActions[TestUtils.URI_KEY_2] = failedAction2

    dispatcher.dispatchNetworkStateChange(info)
    testDispatcher.scheduler.runCurrent()

    assertThat(failedAction1.completedResult).isNotNull()
    assertThat(failedAction2.completedResult).isNotNull()
    assertThat(dispatcher.failedActions).isEmpty()
  }

  @Test fun syncCancelWithMainBeforeHunting() {
    val mainDispatcher = StandardTestDispatcher()
    val dispatcher = createDispatcher(mainContext = mainDispatcher)

    var completed = false
    val action = noopAction(Request.Builder(TestUtils.URI_1).build()) { completed = true }

    // Submit action, will be gated by main
    dispatcher.dispatchSubmit(action)
    testDispatcher.scheduler.runCurrent()
    assertThat(dispatcher.hunterMap[action.request.key]).isNotNull()

    // Cancel action, detaches from hunter but hunter is queued to be submitted
    dispatcher.dispatchCancel(action)
    testDispatcher.scheduler.runCurrent()
    assertThat(dispatcher.hunterMap[action.request.key]).isNotNull()
    assertThat(dispatcher.hunterMap[action.request.key]?.action).isNull()

    // Run main, syncs Dispatcher with main
    mainDispatcher.scheduler.runCurrent()
    // Dispatches the submitted hunter to run
    testDispatcher.scheduler.runCurrent()

    // It isn't hanging around
    assertThat(dispatcher.hunterMap[action.request.key]).isNull()

    // The action is not completed because the hunter never ran
    mainDispatcher.scheduler.runCurrent()
    assertThat(completed).isFalse()
  }

  @Test fun doesntSyncWithMainIfHighPriorityRequestBeforeHunting() {
    val mainDispatcher = StandardTestDispatcher()
    val dispatcher = createDispatcher(mainContext = mainDispatcher)

    var completed = false
    val action = noopAction(Request.Builder(TestUtils.URI_1).priority(HIGH).build()) { completed = true }
    // Submit action
    dispatcher.dispatchSubmit(action)
    testDispatcher.scheduler.runCurrent()
    assertThat(dispatcher.hunterMap[action.request.key]).isNull()

    // Deliver result to main
    mainDispatcher.scheduler.runCurrent()
    assertThat(completed).isTrue()
  }

  private fun createDispatcher(
    scansNetworkChanges: Boolean = true,
    mainContext: CoroutineContext? = null,
    backgroundContext: CoroutineContext? = null
  ): InternalCoroutineDispatcher {
    Mockito.`when`(connectivityManager.activeNetworkInfo).thenReturn(
      if (scansNetworkChanges) Mockito.mock(NetworkInfo::class.java) else null
    )
    Mockito.`when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
    Mockito.`when`(context.checkCallingOrSelfPermission(ArgumentMatchers.anyString())).thenReturn(
      if (scansNetworkChanges) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    )

    testDispatcher = StandardTestDispatcher()
    picasso = TestUtils.mockPicasso(context).newBuilder().dispatchers(mainContext ?: testDispatcher, testDispatcher).build()
    return InternalCoroutineDispatcher(
      context = context,
      mainThreadHandler = Handler(Looper.getMainLooper()),
      cache = cache,
      mainContext = mainContext ?: testDispatcher,
      backgroundContext = backgroundContext ?: testDispatcher
    )
  }

  private fun noopAction(data: Request, onComplete: () -> Unit = { }): Action {
    return object : Action(picasso, data) {
      override fun complete(result: RequestHandler.Result) = onComplete()
      override fun error(e: Exception) = Unit
      override fun getTarget(): Any = this
    }
  }
}
