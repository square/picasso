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

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.drawable.Drawable
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso3.TestUtils.argumentCaptor
import com.squareup.picasso3.TestUtils.eq
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockDrawableTarget
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DrawableTargetActionTest {

  @Test fun invokesSuccessIfTargetIsNotNull() {
    val bitmap = makeBitmap()
    val target = mockDrawableTarget()
    val drawableCaptor = argumentCaptor<PicassoDrawable>()
    val placeholder = mock(Drawable::class.java)
    val action = DrawableTargetAction(
      picasso = TestUtils.mockPicasso(RuntimeEnvironment.application),
      target = target,
      data = TestUtils.SIMPLE_REQUEST,
      noFade = false,
      placeholderDrawable = placeholder,
      errorDrawable = null,
      errorResId = 0
    )

    action.complete(RequestHandler.Result.Bitmap(bitmap, NETWORK))

    Mockito.verify(target).onDrawableLoaded(drawableCaptor.capture(), eq(NETWORK))
    with(drawableCaptor.value) {
      assertThat(this.bitmap).isEqualTo(bitmap)
      assertThat(this.placeholder).isEqualTo(placeholder)
      assertThat(this.animating).isTrue()
    }
  }

  @Test fun invokesOnBitmapFailedIfTargetIsNotNullWithErrorDrawable() {
    val errorDrawable = mock(Drawable::class.java)
    val target = mockDrawableTarget()
    val action = DrawableTargetAction(
      picasso = TestUtils.mockPicasso(RuntimeEnvironment.application),
      target = target,
      data = TestUtils.SIMPLE_REQUEST,
      noFade = true,
      placeholderDrawable = null,
      errorDrawable = errorDrawable,
      errorResId = 0
    )
    val e = RuntimeException()

    action.error(e)

    Mockito.verify(target).onDrawableFailed(e, errorDrawable)
  }

  @Test fun invokesOnBitmapFailedIfTargetIsNotNullWithErrorResourceId() {
    val errorDrawable = mock(Drawable::class.java)
    val context = mock(Context::class.java)
    val dispatcher = mock(Dispatcher::class.java)
    val cache = PlatformLruCache(0)
    val picasso = Picasso(
      context, dispatcher,
      TestUtils.UNUSED_CALL_FACTORY, null, cache, null,
      TestUtils.NO_TRANSFORMERS,
      TestUtils.NO_HANDLERS,
      TestUtils.NO_EVENT_LISTENERS, ARGB_8888, false, false
    )
    val res = mock(Resources::class.java)

    val target = mockDrawableTarget()
    val action = DrawableTargetAction(
      picasso = picasso,
      target = target,
      data = TestUtils.SIMPLE_REQUEST,
      noFade = true,
      placeholderDrawable = null,
      errorDrawable = null,
      errorResId = TestUtils.RESOURCE_ID_1
    )

    Mockito.`when`(context.getDrawable(TestUtils.RESOURCE_ID_1)).thenReturn(errorDrawable)
    val e = RuntimeException()

    action.error(e)

    Mockito.verify(target).onDrawableFailed(e, errorDrawable)
  }

  @Test fun recyclingInSuccessThrowsException() {
    val picasso = TestUtils.mockPicasso(RuntimeEnvironment.application)
    val bitmap = makeBitmap()
    val action = DrawableTargetAction(
      picasso = picasso,
      target = object : DrawableTarget {
        override fun onDrawableLoaded(drawable: Drawable, from: Picasso.LoadedFrom) = (drawable as PicassoDrawable).bitmap.recycle()
        override fun onDrawableFailed(e: Exception, errorDrawable: Drawable?) = throw AssertionError()
        override fun onPrepareLoad(placeHolderDrawable: Drawable?) = throw AssertionError()
      },
      data = TestUtils.SIMPLE_REQUEST,
      noFade = true,
      placeholderDrawable = null,
      errorDrawable = null,
      errorResId = 0
    )

    try {
      action.complete(RequestHandler.Result.Bitmap(bitmap, MEMORY))
      Assert.fail()
    } catch (ignored: IllegalStateException) {
    }
  }
}
