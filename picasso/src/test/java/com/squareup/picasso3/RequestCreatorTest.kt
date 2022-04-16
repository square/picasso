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

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.MemoryPolicy.NO_CACHE
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Picasso.Priority.HIGH
import com.squareup.picasso3.Picasso.Priority.LOW
import com.squareup.picasso3.Picasso.Priority.NORMAL
import com.squareup.picasso3.RemoteViewsAction.AppWidgetAction
import com.squareup.picasso3.RemoteViewsAction.NotificationAction
import com.squareup.picasso3.TestUtils.CUSTOM_HEADER_NAME
import com.squareup.picasso3.TestUtils.CUSTOM_HEADER_VALUE
import com.squareup.picasso3.TestUtils.NO_EVENT_LISTENERS
import com.squareup.picasso3.TestUtils.NO_HANDLERS
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS
import com.squareup.picasso3.TestUtils.STABLE_1
import com.squareup.picasso3.TestUtils.STABLE_URI_KEY_1
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY
import com.squareup.picasso3.TestUtils.URI_1
import com.squareup.picasso3.TestUtils.URI_KEY_1
import com.squareup.picasso3.TestUtils.any
import com.squareup.picasso3.TestUtils.argumentCaptor
import com.squareup.picasso3.TestUtils.eq
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockBitmapTarget
import com.squareup.picasso3.TestUtils.mockCallback
import com.squareup.picasso3.TestUtils.mockDrawableTarget
import com.squareup.picasso3.TestUtils.mockFitImageViewTarget
import com.squareup.picasso3.TestUtils.mockImageViewTarget
import com.squareup.picasso3.TestUtils.mockNotification
import com.squareup.picasso3.TestUtils.mockPicasso
import com.squareup.picasso3.TestUtils.mockRemoteViews
import com.squareup.picasso3.TestUtils.mockRequestCreator
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
class RequestCreatorTest {
  private val actionCaptor = argumentCaptor<Action>()
  private val picasso = spy(mockPicasso(RuntimeEnvironment.application))
  private val bitmap = makeBitmap()

  @Test fun getOnMainCrashes() {
    try {
      RequestCreator(picasso, URI_1, 0).get()
      fail("Calling get() on main thread should throw exception")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun loadWithShutdownCrashes() {
    picasso.shutdown = true
    try {
      RequestCreator(picasso, URI_1, 0).fetch()
      fail("Should have crashed with a shutdown picasso.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun getReturnsNullIfNullUriAndResourceId() {
    val latch = CountDownLatch(1)
    val result = arrayOfNulls<Bitmap>(1)
    Thread {
      try {
        result[0] = RequestCreator(picasso, null, 0).get()
      } catch (e: IOException) {
        fail(e.message)
      } finally {
        latch.countDown()
      }
    }.start()
    latch.await()

    assertThat(result[0]).isNull()
    verify(picasso).defaultBitmapConfig
    verify(picasso).shutdown
    verifyNoMoreInteractions(picasso)
  }

  @Test fun fetchSubmitsFetchRequest() {
    RequestCreator(picasso, URI_1, 0).fetch()
    verify(picasso).submit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(FetchAction::class.java)
  }

  @Test fun fetchWithFitThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).fit().fetch()
      fail("Calling fetch() with fit() should throw an exception")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun fetchWithDefaultPriority() {
    RequestCreator(picasso, URI_1, 0).fetch()
    verify(picasso).submit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(LOW)
  }

  @Test fun fetchWithCustomPriority() {
    RequestCreator(picasso, URI_1, 0).priority(HIGH).fetch()
    verify(picasso).submit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(HIGH)
  }

  @Test fun fetchWithCache() {
    `when`(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap)
    RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).fetch()
    verify(picasso, never()).enqueueAndSubmit(any(Action::class.java))
  }

  @Test fun fetchWithMemoryPolicyNoCache() {
    RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).fetch()
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1)
    verify(picasso).submit(actionCaptor.capture())
  }

  @Test fun intoTargetWithFitThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).fit().into(mockBitmapTarget())
      fail("Calling into() target with fit() should throw exception")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun intoTargetNoPlaceholderCallsWithNull() {
    val target = mockBitmapTarget()
    RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target)
    verify(target).onPrepareLoad(null)
  }

  @Test fun intoTargetWithNullUriAndResourceIdSkipsAndCancels() {
    val target = mockBitmapTarget()
    val placeHolderDrawable = mock(Drawable::class.java)
    RequestCreator(picasso, null, 0).placeholder(placeHolderDrawable).into(target)
    verify(picasso).defaultBitmapConfig
    verify(picasso).shutdown
    verify(picasso).cancelRequest(target)
    verify(target).onPrepareLoad(placeHolderDrawable)
    verifyNoMoreInteractions(picasso)
  }

  @Test fun intoTargetWithQuickMemoryCacheCheckDoesNotSubmit() {
    `when`(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap)
    val target = mockBitmapTarget()
    RequestCreator(picasso, URI_1, 0).into(target)
    verify(target).onBitmapLoaded(bitmap, MEMORY)
    verify(picasso).cancelRequest(target)
    verify(picasso, never()).enqueueAndSubmit(any(Action::class.java))
  }

  @Test fun intoTargetWithSkipMemoryPolicy() {
    val target = mockBitmapTarget()
    RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).into(target)
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1)
  }

  @Test fun intoTargetAndNotInCacheSubmitsTargetRequest() {
    val target = mockBitmapTarget()
    val placeHolderDrawable = mock(Drawable::class.java)
    RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target)
    verify(target).onPrepareLoad(placeHolderDrawable)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(BitmapTargetAction::class.java)
  }

  @Test fun targetActionWithDefaultPriority() {
    RequestCreator(picasso, URI_1, 0).into(mockBitmapTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(NORMAL)
  }

  @Test fun targetActionWithCustomPriority() {
    RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockBitmapTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(HIGH)
  }

  @Test fun targetActionWithDefaultTag() {
    RequestCreator(picasso, URI_1, 0).into(mockBitmapTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo(actionCaptor.value)
  }

  @Test fun targetActionWithCustomTag() {
    RequestCreator(picasso, URI_1, 0).tag("tag").into(mockBitmapTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo("tag")
  }

  @Test fun intoDrawableTargetWithFitThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).fit().into(mockDrawableTarget())
      fail("Calling into() drawable target with fit() should throw exception")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun intoDrawableTargetNoPlaceholderCallsWithNull() {
    val target = mockDrawableTarget()
    RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target)
    verify(target).onPrepareLoad(null)
  }

  @Test fun intoDrawableTargetWithNullUriAndResourceIdSkipsAndCancels() {
    val target = mockDrawableTarget()
    val placeHolderDrawable = mock(Drawable::class.java)
    RequestCreator(picasso, null, 0).placeholder(placeHolderDrawable).into(target)
    verify(picasso).defaultBitmapConfig
    verify(picasso).shutdown
    verify(picasso).cancelRequest(target)
    verify(target).onPrepareLoad(placeHolderDrawable)
    verifyNoMoreInteractions(picasso)
  }

  @Test fun intoDrawableTargetWithQuickMemoryCacheCheckDoesNotSubmit() {
    `when`(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap)
    val target = mockDrawableTarget()
    RequestCreator(picasso, URI_1, 0).into(target)
    verify(target).onDrawableLoaded(any(PicassoDrawable::class.java), eq(MEMORY))
    verify(picasso).cancelRequest(target)
    verify(picasso, never()).enqueueAndSubmit(any(Action::class.java))
  }

  @Test fun intoDrawableTargetWithSkipMemoryPolicy() {
    val target = mockDrawableTarget()
    RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).into(target)
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1)
  }

  @Test fun intoDrawableTargetAndNotInCacheSubmitsTargetRequest() {
    val target = mockDrawableTarget()
    val placeHolderDrawable = mock(Drawable::class.java)
    RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target)
    verify(target).onPrepareLoad(placeHolderDrawable)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(DrawableTargetAction::class.java)
  }

  @Test fun intoImageViewWithNullUriAndResourceIdSkipsAndCancels() {
    val target = mockImageViewTarget()
    RequestCreator(picasso, null, 0).into(target)
    verify(picasso).cancelRequest(target)
    verify(picasso, never()).quickMemoryCacheCheck(anyString())
    verify(picasso, never()).enqueueAndSubmit(any(Action::class.java))
  }

  @Test fun intoImageViewWithQuickMemoryCacheCheckDoesNotSubmit() {
    val cache = PlatformLruCache(0)
    val picasso = spy(
      Picasso(
        RuntimeEnvironment.application, mock(Dispatcher::class.java), UNUSED_CALL_FACTORY,
        null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
        indicatorsEnabled = false, isLoggingEnabled = false
      )
    )
    doReturn(bitmap).`when`(picasso).quickMemoryCacheCheck(URI_KEY_1)
    val target = mockImageViewTarget()
    val callback = mockCallback()
    RequestCreator(picasso, URI_1, 0).into(target, callback)
    verify(target).setImageDrawable(any(PicassoDrawable::class.java))
    verify(callback).onSuccess()
    verify(picasso).cancelRequest(target)
    verify(picasso, never()).enqueueAndSubmit(any(Action::class.java))
  }

  @Test fun intoImageViewSetsPlaceholderDrawable() {
    val cache = PlatformLruCache(0)
    val picasso = spy(
      Picasso(
        RuntimeEnvironment.application, mock(Dispatcher::class.java), UNUSED_CALL_FACTORY,
        null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
        false, false
      )
    )
    val target = mockImageViewTarget()
    val placeHolderDrawable = mock(Drawable::class.java)
    RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target)
    verify(target).setImageDrawable(placeHolderDrawable)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(ImageViewAction::class.java)
  }

  @Test fun intoImageViewNoPlaceholderDrawable() {
    val cache = PlatformLruCache(0)
    val picasso = spy(
      Picasso(
        RuntimeEnvironment.application, mock(Dispatcher::class.java), UNUSED_CALL_FACTORY,
        null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
        indicatorsEnabled = false, isLoggingEnabled = false
      )
    )
    val target = mockImageViewTarget()
    RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target)
    verifyNoMoreInteractions(target)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(ImageViewAction::class.java)
  }

  @Test fun intoImageViewSetsPlaceholderWithResourceId() {
    val cache = PlatformLruCache(0)
    val picasso = spy(
      Picasso(
        RuntimeEnvironment.application, mock(Dispatcher::class.java), UNUSED_CALL_FACTORY,
        null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
        indicatorsEnabled = false, isLoggingEnabled = false
      )
    )
    val target = mockImageViewTarget()
    RequestCreator(picasso, URI_1, 0).placeholder(android.R.drawable.picture_frame).into(target)
    val drawableCaptor = ArgumentCaptor.forClass(Drawable::class.java)
    verify(target).setImageDrawable(drawableCaptor.capture())
    assertThat(shadowOf(drawableCaptor.value).createdFromResId)
      .isEqualTo(android.R.drawable.picture_frame)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(ImageViewAction::class.java)
  }

  @Test fun cancelNotOnMainThreadCrashes() {
    doCallRealMethod().`when`(picasso).cancelRequest(any(BitmapTarget::class.java))
    val latch = CountDownLatch(1)
    Thread {
      try {
        RequestCreator(picasso, null, 0).into(mockBitmapTarget())
        fail("Should have thrown IllegalStateException")
      } catch (ignored: IllegalStateException) {
      } finally {
        latch.countDown()
      }
    }.start()
    latch.await()
  }

  @Test fun intoNotOnMainThreadCrashes() {
    doCallRealMethod().`when`(picasso).enqueueAndSubmit(any(Action::class.java))
    val latch = CountDownLatch(1)
    Thread {
      try {
        RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget())
        fail("Should have thrown IllegalStateException")
      } catch (ignored: IllegalStateException) {
      } finally {
        latch.countDown()
      }
    }.start()
    latch.await()
  }

  @Test fun intoImageViewAndNotInCacheSubmitsImageViewRequest() {
    val target = mockImageViewTarget()
    RequestCreator(picasso, URI_1, 0).into(target)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(ImageViewAction::class.java)
  }

  @Test fun intoImageViewWithFitAndNoDimensionsQueuesDeferredImageViewRequest() {
    val target = mockFitImageViewTarget(true)
    `when`(target.width).thenReturn(0)
    `when`(target.height).thenReturn(0)
    RequestCreator(picasso, URI_1, 0).fit().into(target)
    verify(picasso, never()).enqueueAndSubmit(any(Action::class.java))
    verify(picasso).defer(eq(target), any(DeferredRequestCreator::class.java))
  }

  @Test fun intoImageViewWithFitAndDimensionsQueuesImageViewRequest() {
    val target = mockFitImageViewTarget(true)
    `when`(target.width).thenReturn(100)
    `when`(target.height).thenReturn(100)
    RequestCreator(picasso, URI_1, 0).fit().into(target)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(ImageViewAction::class.java)
  }

  @Test fun intoImageViewWithSkipMemoryCachePolicy() {
    val target = mockImageViewTarget()
    RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).into(target)
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1)
  }

  @Test fun intoImageViewWithFitAndResizeThrows() {
    try {
      val target = mockImageViewTarget()
      RequestCreator(picasso, URI_1, 0).fit().resize(10, 10).into(target)
      fail("Calling into() ImageView with fit() and resize() should throw exception")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun imageViewActionWithDefaultPriority() {
    RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(NORMAL)
  }

  @Test fun imageViewActionWithCustomPriority() {
    RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockImageViewTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(HIGH)
  }

  @Test fun imageViewActionWithDefaultTag() {
    RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo(actionCaptor.value)
  }

  @Test fun imageViewActionWithCustomTag() {
    RequestCreator(picasso, URI_1, 0).tag("tag").into(mockImageViewTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo("tag")
  }

  @Test fun intoRemoteViewsWidgetQueuesAppWidgetAction() {
    RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, intArrayOf(1, 2, 3))
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(AppWidgetAction::class.java)
  }

  @Test fun intoRemoteViewsNotificationQueuesNotificationAction() {
    RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, mockNotification())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value).isInstanceOf(NotificationAction::class.java)
  }

  @Test fun intoRemoteViewsWidgetWithPlaceholderDrawableThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).placeholder(ColorDrawable(0))
        .into(mockRemoteViews(), 0, intArrayOf(1, 2, 3))
      fail("Calling into() with placeholder drawable should throw exception")
    } catch (ignored: IllegalArgumentException) {
    }
  }

  @Test fun intoRemoteViewsWidgetWithErrorDrawableThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).error(ColorDrawable(0))
        .into(mockRemoteViews(), 0, intArrayOf(1, 2, 3))
      fail("Calling into() with error drawable should throw exception")
    } catch (ignored: IllegalArgumentException) {
    }
  }

  @Test fun intoRemoteViewsNotificationWithPlaceholderDrawableThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).placeholder(ColorDrawable(0))
        .into(mockRemoteViews(), 0, 0, mockNotification())
      fail("Calling into() with error drawable should throw exception")
    } catch (ignored: IllegalArgumentException) {
    }
  }

  @Test fun intoRemoteViewsNotificationWithErrorDrawableThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).error(ColorDrawable(0))
        .into(mockRemoteViews(), 0, 0, mockNotification())
      fail("Calling into() with error drawable should throw exception")
    } catch (ignored: IllegalArgumentException) {
    }
  }

  @Test fun intoRemoteViewsWidgetWithFitThrows() {
    try {
      val remoteViews = mockRemoteViews()
      RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, intArrayOf(1, 2, 3))
      fail("Calling fit() into remote views should throw exception")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun intoRemoteViewsNotificationWithFitThrows() {
    try {
      val remoteViews = mockRemoteViews()
      RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, 1, mockNotification())
      fail("Calling fit() into remote views should throw exception")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun intoTargetNoResizeWithCenterInsideOrCenterCropThrows() {
    try {
      RequestCreator(picasso, URI_1, 0).centerInside().into(mockBitmapTarget())
      fail("Center inside with unknown width should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
    try {
      RequestCreator(picasso, URI_1, 0).centerCrop().into(mockBitmapTarget())
      fail("Center inside with unknown height should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun appWidgetActionWithDefaultPriority() {
    RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, intArrayOf(1, 2, 3))
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(NORMAL)
  }

  @Test fun appWidgetActionWithCustomPriority() {
    RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockRemoteViews(), 0, intArrayOf(1, 2, 3))
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(HIGH)
  }

  @Test fun notificationActionWithDefaultPriority() {
    RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, mockNotification())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(NORMAL)
  }

  @Test fun notificationActionWithCustomPriority() {
    RequestCreator(picasso, URI_1, 0).priority(HIGH)
      .into(mockRemoteViews(), 0, 0, mockNotification())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.priority).isEqualTo(HIGH)
  }

  @Test fun appWidgetActionWithDefaultTag() {
    RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, intArrayOf(1, 2, 3))
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo(actionCaptor.value)
  }

  @Test fun appWidgetActionWithCustomTag() {
    RequestCreator(picasso, URI_1, 0).tag("tag")
      .into(remoteViews = mockRemoteViews(), viewId = 0, appWidgetIds = intArrayOf(1, 2, 3))
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo("tag")
  }

  @Test fun notificationActionWithDefaultTag() {
    RequestCreator(picasso, URI_1, 0)
      .into(
        remoteViews = mockRemoteViews(),
        viewId = 0,
        notificationId = 0,
        notification = mockNotification()
      )
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo(actionCaptor.value)
  }

  @Test fun notificationActionWithCustomTag() {
    RequestCreator(picasso, URI_1, 0).tag("tag")
      .into(mockRemoteViews(), 0, 0, mockNotification())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.tag).isEqualTo("tag")
  }

  @Test fun invalidResize() {
    try {
      mockRequestCreator(picasso).resize(-1, 10)
      fail("Negative width should throw exception.")
    } catch (ignored: IllegalArgumentException) {
    }
    try {
      mockRequestCreator(picasso).resize(10, -1)
      fail("Negative height should throw exception.")
    } catch (ignored: IllegalArgumentException) {
    }
    try {
      mockRequestCreator(picasso).resize(0, 0)
      fail("Zero dimensions should throw exception.")
    } catch (ignored: IllegalArgumentException) {
    }
  }

  @Test fun invalidCenterCrop() {
    try {
      mockRequestCreator(picasso).resize(10, 10).centerInside().centerCrop()
      fail("Calling center crop after center inside should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun invalidCenterInside() {
    try {
      mockRequestCreator(picasso).resize(10, 10).centerCrop().centerInside()
      fail("Calling center inside after center crop should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun invalidPlaceholderImage() {
    try {
      mockRequestCreator(picasso).placeholder(0)
      fail("Resource ID of zero should throw exception.")
    } catch (ignored: IllegalArgumentException) {
    }
    try {
      mockRequestCreator(picasso).placeholder(1).placeholder(ColorDrawable(0))
      fail("Two placeholders should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
    try {
      mockRequestCreator(picasso).placeholder(ColorDrawable(0)).placeholder(1)
      fail("Two placeholders should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun invalidNoPlaceholder() {
    try {
      mockRequestCreator(picasso).noPlaceholder().placeholder(ColorDrawable(0))
      fail("Placeholder after no placeholder should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
    try {
      mockRequestCreator(picasso).noPlaceholder().placeholder(1)
      fail("Placeholder after no placeholder should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
    try {
      mockRequestCreator(picasso).placeholder(1).noPlaceholder()
      fail("No placeholder after placeholder should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
    try {
      mockRequestCreator(picasso).placeholder(ColorDrawable(0)).noPlaceholder()
      fail("No placeholder after placeholder should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun invalidErrorImage() {
    try {
      mockRequestCreator(picasso).error(0)
      fail("Resource ID of zero should throw exception.")
    } catch (ignored: IllegalArgumentException) {
    }
    try {
      mockRequestCreator(picasso).error(1).error(ColorDrawable(0))
      fail("Two error placeholders should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
    try {
      mockRequestCreator(picasso).error(ColorDrawable(0)).error(1)
      fail("Two error placeholders should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun invalidPriority() {
    try {
      mockRequestCreator(picasso).priority(LOW).priority(HIGH)
      fail("Two priorities should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun alreadySetTagThrows() {
    try {
      mockRequestCreator(picasso).tag("tag1").tag("tag2")
      fail("Two tags should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun transformationListImplementationValid() {
    val transformations = listOf(TestTransformation("test"))
    mockRequestCreator(picasso).transform(transformations)
    // TODO verify something!
  }

  @Test fun imageViewActionWithStableKey() {
    RequestCreator(picasso, URI_1, 0).stableKey(STABLE_1).into(mockImageViewTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.key).isEqualTo(STABLE_URI_KEY_1)
  }

  @Test fun imageViewActionWithCustomHeaders() {
    RequestCreator(picasso, URI_1, 0)
      .addHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
      .into(mockImageViewTarget())
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())
    assertThat(actionCaptor.value.request.headers!![CUSTOM_HEADER_NAME])
      .isEqualTo(CUSTOM_HEADER_VALUE)
  }
}
