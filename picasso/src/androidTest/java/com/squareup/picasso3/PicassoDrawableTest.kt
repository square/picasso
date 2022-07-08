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
import android.graphics.Color.RED
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.Picasso.LoadedFrom.DISK
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PicassoDrawableTest {
  private val placeholder: Drawable = ColorDrawable(RED)
  private val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8)

  @Test fun createWithNoPlaceholderAnimation() {
    val pd = PicassoDrawable(
      placeholder = null,
      context = ApplicationProvider.getApplicationContext(),
      bitmap = bitmap,
      loadedFrom = DISK,
      noFade = false,
      debugging = false
    )
    assertThat(pd.bitmap).isSameInstanceAs(bitmap)
    assertThat(pd.placeholder).isNull()
    assertThat(pd.animating).isTrue()
  }

  @Test fun createWithPlaceholderAnimation() {
    val pd = PicassoDrawable(
      context = ApplicationProvider.getApplicationContext(),
      bitmap = bitmap,
      placeholder,
      loadedFrom = DISK,
      noFade = false,
      debugging = false
    )
    assertThat(pd.bitmap).isSameInstanceAs(bitmap)
    assertThat(pd.placeholder).isSameInstanceAs(placeholder)
    assertThat(pd.animating).isTrue()
  }

  @Test fun createWithBitmapCacheHit() {
    val pd = PicassoDrawable(
      context = ApplicationProvider.getApplicationContext(),
      bitmap = bitmap,
      placeholder,
      loadedFrom = Picasso.LoadedFrom.MEMORY,
      noFade = false,
      debugging = false
    )
    assertThat(pd.bitmap).isSameInstanceAs(bitmap)
    assertThat(pd.placeholder).isNull()
    assertThat(pd.animating).isFalse()
  }
}
