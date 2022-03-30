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
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.MediaStoreRequestHandler.Companion.getPicassoKind
import com.squareup.picasso3.MediaStoreRequestHandler.PicassoKind.FULL
import com.squareup.picasso3.MediaStoreRequestHandler.PicassoKind.MICRO
import com.squareup.picasso3.MediaStoreRequestHandler.PicassoKind.MINI
import com.squareup.picasso3.RequestHandler.Callback
import com.squareup.picasso3.Shadows.ShadowImageThumbnails
import com.squareup.picasso3.Shadows.ShadowVideoThumbnails
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_1_URL
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_2_URL
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_KEY_1
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_KEY_2
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockAction
import com.squareup.picasso3.TestUtils.mockPicasso
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowVideoThumbnails::class, ShadowImageThumbnails::class])
class MediaStoreRequestHandlerTest {
  private lateinit var context: Context
  private lateinit var picasso: Picasso

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication().applicationContext
    picasso = mockPicasso(context)
    Robolectric.setupContentProvider(TestContentProvider::class.java, "media")
  }

  @Test fun decodesVideoThumbnailWithVideoMimeType() {
    val bitmap = makeBitmap()
    val request = Request.Builder(
      uri = MEDIA_STORE_CONTENT_2_URL,
      resourceId = 0,
      bitmapConfig = ARGB_8888
    )
      .stableKey(MEDIA_STORE_CONTENT_KEY_2)
      .resize(100, 100)
      .build()
    val action = mockAction(picasso, request)
    val requestHandler = MediaStoreRequestHandler(context)
    requestHandler.load(
      picasso = picasso,
      request = action.request,
      callback = object : Callback {
        override fun onSuccess(result: RequestHandler.Result?) =
          assertBitmapsEqual((result as RequestHandler.Result.Bitmap?)!!.bitmap, bitmap)

        override fun onError(t: Throwable) = fail(t.message)
      }
    )
  }

  @Test fun decodesImageThumbnailWithImageMimeType() {
    val bitmap = makeBitmap(20, 20)
    val request = Request.Builder(
      uri = MEDIA_STORE_CONTENT_1_URL,
      resourceId = 0,
      bitmapConfig = ARGB_8888
    )
      .stableKey(MEDIA_STORE_CONTENT_KEY_1)
      .resize(100, 100)
      .build()
    val action = mockAction(picasso, request)
    val requestHandler = MediaStoreRequestHandler(context)
    requestHandler.load(
      picasso = picasso,
      request = action.request,
      callback = object : Callback {
        override fun onSuccess(result: RequestHandler.Result?) =
          assertBitmapsEqual((result as RequestHandler.Result.Bitmap?)!!.bitmap, bitmap)

        override fun onError(t: Throwable) = fail(t.message)
      }
    )
  }

  @Test fun getPicassoKindMicro() {
    assertThat(getPicassoKind(96, 96)).isEqualTo(MICRO)
    assertThat(getPicassoKind(95, 95)).isEqualTo(MICRO)
  }

  @Test fun getPicassoKindMini() {
    assertThat(getPicassoKind(512, 384)).isEqualTo(MINI)
    assertThat(getPicassoKind(100, 100)).isEqualTo(MINI)
  }

  @Test fun getPicassoKindFull() {
    assertThat(getPicassoKind(513, 385)).isEqualTo(FULL)
    assertThat(getPicassoKind(1000, 1000)).isEqualTo(FULL)
    assertThat(getPicassoKind(1000, 384)).isEqualTo(FULL)
    assertThat(getPicassoKind(1000, 96)).isEqualTo(FULL)
    assertThat(getPicassoKind(96, 1000)).isEqualTo(FULL)
  }

  private fun assertBitmapsEqual(a: Bitmap, b: Bitmap) {
    assertThat(a.height).isEqualTo(b.height)
    assertThat(a.width).isEqualTo(b.width)
    assertThat(shadowOf(a).description).isEqualTo(shadowOf(b).description)
  }
}
