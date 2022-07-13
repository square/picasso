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
import android.graphics.Bitmap.Config.RGB_565
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.BitmapUtils.calculateInSampleSize
import com.squareup.picasso3.BitmapUtils.createBitmapOptions
import com.squareup.picasso3.BitmapUtils.requiresInSampleSize
import com.squareup.picasso3.TestUtils.URI_1
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapUtilsTest {

  @Test fun bitmapConfig() {
    for (config in Bitmap.Config.values()) {
      val data = Request.Builder(URI_1).config(config).build()
      val copy = data.newBuilder().build()

      assertThat(createBitmapOptions(data)!!.inPreferredConfig).isSameInstanceAs(config)
      assertThat(createBitmapOptions(copy)!!.inPreferredConfig).isSameInstanceAs(config)
    }
  }

  @Test fun requiresComputeInSampleSize() {
    assertThat(requiresInSampleSize(null)).isFalse()

    val defaultOptions = BitmapFactory.Options()
    assertThat(requiresInSampleSize(defaultOptions)).isFalse()

    val justBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    assertThat(requiresInSampleSize(justBounds)).isTrue()
  }

  @Test fun calculateInSampleSizeNoResize() {
    val options = BitmapFactory.Options()
    val data = Request.Builder(URI_1).build()
    calculateInSampleSize(100, 100, 150, 150, options, data)
    assertThat(options.inSampleSize).isEqualTo(1)
  }

  @Test fun calculateInSampleSizeResize() {
    val options = BitmapFactory.Options()
    val data = Request.Builder(URI_1).build()
    calculateInSampleSize(100, 100, 200, 200, options, data)
    assertThat(options.inSampleSize).isEqualTo(2)
  }

  @Test fun calculateInSampleSizeResizeCenterInside() {
    val options = BitmapFactory.Options()
    val data = Request.Builder(URI_1).centerInside().resize(100, 100).build()
    calculateInSampleSize(data.targetWidth, data.targetHeight, 400, 200, options, data)
    assertThat(options.inSampleSize).isEqualTo(4)
  }

  @Test fun calculateInSampleSizeKeepAspectRatioWithWidth() {
    val options = BitmapFactory.Options()
    val data = Request.Builder(URI_1).resize(400, 0).build()
    calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data)
    assertThat(options.inSampleSize).isEqualTo(2)
  }

  @Test fun calculateInSampleSizeKeepAspectRatioWithHeight() {
    val options = BitmapFactory.Options()
    val data = Request.Builder(URI_1).resize(0, 100).build()
    calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data)
    assertThat(options.inSampleSize).isEqualTo(2)
  }

  @Test fun nullBitmapOptionsIfNoResizing() {
    // No resize must return no bitmap options
    val noResize = Request.Builder(URI_1).build()
    val noResizeOptions = createBitmapOptions(noResize)
    assertThat(noResizeOptions).isNull()
  }

  @Test fun inJustDecodeBoundsIfResizing() {
    // Resize must return bitmap options with inJustDecodeBounds = true
    val requiresResize = Request.Builder(URI_1).resize(20, 15).build()
    val resizeOptions = createBitmapOptions(requiresResize)
    assertThat(resizeOptions).isNotNull()
    assertThat(resizeOptions!!.inJustDecodeBounds).isTrue()
  }

  @Test fun createWithConfigAndNotInJustDecodeBounds() {
    // Given a config, must return bitmap options and false inJustDecodeBounds
    val config = Request.Builder(URI_1).config(RGB_565).build()
    val configOptions = createBitmapOptions(config)
    assertThat(configOptions).isNotNull()
    assertThat(configOptions!!.inJustDecodeBounds).isFalse()
  }
}
