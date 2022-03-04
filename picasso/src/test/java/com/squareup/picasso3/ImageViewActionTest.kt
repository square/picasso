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

import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import com.squareup.picasso3.TestUtils.NO_EVENT_LISTENERS
import com.squareup.picasso3.TestUtils.NO_HANDLERS
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS
import com.squareup.picasso3.TestUtils.RESOURCE_ID_1
import com.squareup.picasso3.TestUtils.SIMPLE_REQUEST
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockCallback
import com.squareup.picasso3.TestUtils.mockImageViewTarget
import com.squareup.picasso3.TestUtils.mockPicasso
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ImageViewActionTest {

  @Test
  fun invokesTargetAndCallbackSuccessIfTargetIsNotNull() {
    val bitmap = makeBitmap()
    val dispatcher = mock(Dispatcher::class.java)
    val cache = PlatformLruCache(0)
    val picasso = Picasso(
      RuntimeEnvironment.application, dispatcher, UNUSED_CALL_FACTORY, null, cache, null,
      NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888, false, false
    )
    val target = mockImageViewTarget()
    val callback = mockCallback()
    val request = ImageViewAction(
      picasso = picasso,
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = 0,
      noFade = false,
      callback = callback
    )
    request.complete(Bitmap(bitmap, MEMORY))
    verify(target).setImageDrawable(any(PicassoDrawable::class.java))
    verify(callback).onSuccess()
  }

  @Test
  fun invokesTargetAndCallbackErrorIfTargetIsNotNullWithErrorResourceId() {
    val target = mockImageViewTarget()
    val callback = mockCallback()
    val request = ImageViewAction(
      picasso = mockPicasso(RuntimeEnvironment.application),
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = RESOURCE_ID_1,
      noFade = false,
      callback = callback
    )
    val e = RuntimeException()

    request.error(e)

    verify(target).setImageResource(RESOURCE_ID_1)
    verify(callback).onError(e)
  }

  @Test fun invokesErrorIfTargetIsNotNullWithErrorResourceId() {
    val target = mockImageViewTarget()
    val callback = mockCallback()
    val request = ImageViewAction(
      picasso = mockPicasso(RuntimeEnvironment.application),
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = RESOURCE_ID_1,
      noFade = false,
      callback = callback
    )
    val e = RuntimeException()

    request.error(e)
    verify(target).setImageResource(RESOURCE_ID_1)
    verify(callback).onError(e)
  }

  @Test fun invokesErrorIfTargetIsNotNullWithErrorDrawable() {
    val errorDrawable = mock(Drawable::class.java)
    val target = mockImageViewTarget()
    val callback = mockCallback()
    val request = ImageViewAction(
      picasso = mockPicasso(RuntimeEnvironment.application),
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = errorDrawable,
      errorResId = 0,
      noFade = false,
      callback = callback
    )
    val e = RuntimeException()

    request.error(e)

    verify(target).setImageDrawable(errorDrawable)
    verify(callback).onError(e)
  }

  @Test fun clearsCallbackOnCancel() {
    val picasso = mockPicasso(RuntimeEnvironment.application)
    val target = mockImageViewTarget()
    val callback = mockCallback()
    val request = ImageViewAction(
      picasso = picasso,
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = 0,
      noFade = false,
      callback = callback
    )
    request.cancel()
    assertThat(request.callback).isNull()
  }

  @Test fun stopPlaceholderAnimationOnError() {
    val picasso = mockPicasso(RuntimeEnvironment.application)
    val placeholder = mock(AnimationDrawable::class.java)
    val target = mockImageViewTarget()
    `when`(target.drawable).thenReturn(placeholder)
    val request = ImageViewAction(
      picasso = picasso,
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = 0,
      noFade = false,
      callback = null
    )
    request.error(RuntimeException())
    verify(placeholder).stop()
  }
}
