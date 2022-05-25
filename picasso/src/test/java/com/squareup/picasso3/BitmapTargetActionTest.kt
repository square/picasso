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
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.drawable.Drawable
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.TestUtils.NO_EVENT_LISTENERS
import com.squareup.picasso3.TestUtils.NO_HANDLERS
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS
import com.squareup.picasso3.TestUtils.RESOURCE_ID_1
import com.squareup.picasso3.TestUtils.SIMPLE_REQUEST
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockBitmapTarget
import com.squareup.picasso3.TestUtils.mockPicasso
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BitmapTargetActionTest {

  @Test fun invokesSuccessIfTargetIsNotNull() {
    val bitmap = makeBitmap()
    val target = mockBitmapTarget()
    val request = BitmapTargetAction(
      picasso = mockPicasso(RuntimeEnvironment.application),
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = 0
    )
    request.complete(RequestHandler.Result.Bitmap(bitmap, MEMORY))
    verify(target).onBitmapLoaded(bitmap, MEMORY)
  }

  @Test fun invokesOnBitmapFailedIfTargetIsNotNullWithErrorDrawable() {
    val errorDrawable = mock(Drawable::class.java)
    val target = mockBitmapTarget()
    val request = BitmapTargetAction(
      picasso = mockPicasso(RuntimeEnvironment.application),
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = errorDrawable,
      errorResId = 0
    )
    val e = RuntimeException()

    request.error(e)

    verify(target).onBitmapFailed(e, errorDrawable)
  }

  @Test fun invokesOnBitmapFailedIfTargetIsNotNullWithErrorResourceId() {
    val errorDrawable = mock(Drawable::class.java)
    val target = mockBitmapTarget()
    val context = mock(Context::class.java)
    val dispatcher = mock(Dispatcher::class.java)
    val cache = PlatformLruCache(0)
    val picasso = Picasso(
      context, dispatcher, UNUSED_CALL_FACTORY, null, cache, null,
      NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888, false, false
    )
    val request = BitmapTargetAction(
      picasso = picasso,
      target = target,
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = RESOURCE_ID_1
    )

    `when`(context.getDrawable(RESOURCE_ID_1)).thenReturn(errorDrawable)
    val e = RuntimeException()

    request.error(e)
    verify(target).onBitmapFailed(e, errorDrawable)
  }

  @Test fun recyclingInSuccessThrowsException() {
    val picasso = mockPicasso(RuntimeEnvironment.application)
    val bitmap = makeBitmap()
    val tr = BitmapTargetAction(
      picasso = picasso,
      target = object : BitmapTarget {
        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) = bitmap.recycle()
        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) = fail()
        override fun onPrepareLoad(placeHolderDrawable: Drawable?) = fail()
      },
      data = SIMPLE_REQUEST,
      errorDrawable = null,
      errorResId = 0
    )
    try {
      tr.complete(RequestHandler.Result.Bitmap(bitmap, MEMORY))
      fail()
    } catch (ignored: IllegalStateException) {
    }
  }
}
