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
import android.graphics.Color.RED
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.google.common.truth.Truth.assertThat
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.squareup.picasso3.Picasso.LoadedFrom.DISK
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.TestUtils.makeBitmap
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class PicassoDrawableTest {
  private val context: Context = RuntimeEnvironment.application
  private val placeholder: Drawable = ColorDrawable(RED)
  private val bitmap = makeBitmap()

  @Test fun createWithNoPlaceholderAnimation() {
    val pd = PicassoDrawable(context, bitmap,
      placeholder = null,
      loadedFrom = DISK,
      noFade = false,
      debugging = false
    )
    assertThat(pd.bitmap).isSameInstanceAs(bitmap)
    assertThat(pd.placeholder).isNull()
    assertThat(pd.animating).isTrue()
  }

  @Test fun createWithPlaceholderAnimation() {
    val pd = PicassoDrawable(context, bitmap, placeholder,
      loadedFrom = DISK,
      noFade = false,
      debugging = false
    )
    assertThat(pd.bitmap).isSameInstanceAs(bitmap)
    assertThat(pd.placeholder).isSameInstanceAs(placeholder)
    assertThat(pd.animating).isTrue()
  }

  @Test fun createWithBitmapCacheHit() {
    val pd = PicassoDrawable(context, bitmap, placeholder,
      loadedFrom = MEMORY,
      noFade = false,
      debugging = false
    )
    assertThat(pd.bitmap).isSameInstanceAs(bitmap)
    assertThat(pd.placeholder).isNull()
    assertThat(pd.animating).isFalse()
  }
}