/*
 * Copyright (C) 2014 Square, Inc.
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

import android.graphics.Bitmap.Config.ARGB_8888
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget
import com.squareup.picasso3.TestUtils.NO_EVENT_LISTENERS
import com.squareup.picasso3.TestUtils.NO_HANDLERS
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS
import com.squareup.picasso3.TestUtils.SIMPLE_REQUEST
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockCallback
import com.squareup.picasso3.TestUtils.mockImageViewTarget
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RemoteViewsActionTest {
  private lateinit var picasso: Picasso
  private lateinit var remoteViews: RemoteViews

  @Before fun setUp() {
    picasso = Picasso(
      context = RuntimeEnvironment.application,
      dispatcher = mock(Dispatcher::class.java),
      callFactory = UNUSED_CALL_FACTORY,
      closeableCache = null,
      cache = PlatformLruCache(0),
      listener = null,
      requestTransformers = NO_TRANSFORMERS,
      extraRequestHandlers = NO_HANDLERS,
      eventListeners = NO_EVENT_LISTENERS,
      defaultBitmapConfig = ARGB_8888,
      indicatorsEnabled = false,
      isLoggingEnabled = false
    )
    remoteViews = mock(RemoteViews::class.java)
    `when`(remoteViews.layoutId).thenReturn(android.R.layout.list_content)
  }

  @Test fun completeSetsBitmapOnRemoteViews() {
    val callback = mockCallback()
    val bitmap = makeBitmap()
    val action = createAction(callback)
    action.complete(RequestHandler.Result.Bitmap(bitmap, NETWORK))
    verify(remoteViews).setImageViewBitmap(1, bitmap)
    verify(callback).onSuccess()
  }

  @Test fun errorWithNoResourceIsNoop() {
    val callback = mockCallback()
    val action = createAction(callback)
    val e = RuntimeException()
    action.error(e)
    verifyZeroInteractions(remoteViews)
    verify(callback).onError(e)
  }

  @Test fun errorWithResourceSetsResource() {
    val callback = mockCallback()
    val action = createAction(callback, 1)
    val e = RuntimeException()
    action.error(e)
    verify(remoteViews).setImageViewResource(1, 1)
    verify(callback).onError(e)
  }

  @Test fun clearsCallbackOnCancel() {
    val request = ImageViewAction(
      picasso = picasso,
      target = mockImageViewTarget(),
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = 0,
      noFade = false,
      callback = mockCallback()
    )
    request.cancel()
    assertThat(request.callback).isNull()
  }

  private fun createAction(callback: Callback, errorResId: Int = 0): TestableRemoteViewsAction {
    return TestableRemoteViewsAction(
      picasso = picasso,
      data = SIMPLE_REQUEST,
      errorResId = errorResId,
      target = RemoteViewsTarget(remoteViews, 1),
      callback = callback
    )
  }

  private class TestableRemoteViewsAction(
    picasso: Picasso,
    data: Request,
    @DrawableRes errorResId: Int,
    target: RemoteViewsTarget,
    callback: Callback?
  ) : RemoteViewsAction(picasso, data, errorResId, target, callback) {
    override fun update() {}
    override fun getTarget(): Any = target
  }
}
