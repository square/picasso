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
package com.squareup.picasso3.pollexor

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.Request.Builder
import com.squareup.pollexor.Thumbor
import com.squareup.pollexor.ThumborUrlBuilder
import com.squareup.pollexor.ThumborUrlBuilder.ImageFormat.WEBP
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PollexorRequestTransformerTest {
  private val transformer = PollexorRequestTransformer(Thumbor.create(HOST))
  private val secureTransformer = PollexorRequestTransformer(Thumbor.create(HOST, KEY))
  private val alwaysResizeTransformer = PollexorRequestTransformer(
    Thumbor.create(HOST), alwaysTransform = true
  )
  private val callbackTransformer = PollexorRequestTransformer(
    Thumbor.create(HOST), callback = { it.filter("custom") }
  )

  @Test fun resourceIdRequestsAreNotTransformed() {
    val input = Builder(12).build()
    val output = transformer.transformRequest(input)
    assertThat(output).isSameInstanceAs(input)
  }

  @Test fun resourceIdRequestsAreNotTransformedWhenAlwaysTransformIsTrue() {
    val input = Builder(12).build()
    val output = alwaysResizeTransformer.transformRequest(input)
    assertThat(output).isSameInstanceAs(input)
  }

  @Test fun nonHttpRequestsAreNotTransformed() {
    val input = Builder(IMAGE_URI).build()
    val output = transformer.transformRequest(input)
    assertThat(output).isSameInstanceAs(input)
  }

  @Test fun nonResizedRequestsAreNotTransformed() {
    val input = Builder(IMAGE_URI).build()
    val output = transformer.transformRequest(input)
    assertThat(output).isSameInstanceAs(input)
  }

  @Test fun nonResizedRequestsAreTransformedWhenAlwaysTransformIsSet() {
    val input = Builder(IMAGE_URI).build()
    val output = alwaysResizeTransformer.transformRequest(input)
    assertThat(output).isNotSameInstanceAs(input)
    assertThat(output.hasSize()).isFalse()

    val expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl()
    assertThat(output.uri.toString()).isEqualTo(expected)
  }

  @Test fun simpleResize() {
    val input = Builder(IMAGE_URI).resize(50, 50).build()
    val output = transformer.transformRequest(input)
    assertThat(output).isNotSameInstanceAs(input)
    assertThat(output.hasSize()).isFalse()

    val expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl()
    assertThat(output.uri.toString()).isEqualTo(expected)
  }

  @Test fun simpleResizeWithCenterCrop() {
    val input = Builder(IMAGE_URI).resize(50, 50).centerCrop().build()
    val output = transformer.transformRequest(input)
    assertThat(output).isNotSameInstanceAs(input)
    assertThat(output.hasSize()).isFalse()
    assertThat(output.centerCrop).isFalse()

    val expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl()
    assertThat(output.uri.toString()).isEqualTo(expected)
  }

  @Test fun simpleResizeWithCenterInside() {
    val input = Builder(IMAGE_URI).resize(50, 50).centerInside().build()
    val output = transformer.transformRequest(input)
    assertThat(output).isNotSameInstanceAs(input)
    assertThat(output.hasSize()).isFalse()
    assertThat(output.centerInside).isFalse()

    val expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .fitIn()
      .toUrl()
    assertThat(output.uri.toString()).isEqualTo(expected)
  }

  @Test fun simpleResizeWithEncryption() {
    val input = Builder(IMAGE_URI).resize(50, 50).build()
    val output = secureTransformer.transformRequest(input)
    assertThat(output).isNotSameInstanceAs(input)
    assertThat(output.hasSize()).isFalse()

    val expected = Thumbor.create(HOST, KEY)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl()
    assertThat(output.uri.toString()).isEqualTo(expected)
  }

  @Test fun simpleResizeWithCenterInsideAndEncryption() {
    val input = Builder(IMAGE_URI).resize(50, 50).centerInside().build()
    val output = secureTransformer.transformRequest(input)
    assertThat(output).isNotSameInstanceAs(input)
    assertThat(output.hasSize()).isFalse()
    assertThat(output.centerInside).isFalse()

    val expected = Thumbor.create(HOST, KEY)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .fitIn()
      .toUrl()
    assertThat(output.uri.toString()).isEqualTo(expected)
  }

  @Test fun configureCallback() {
    val input = Builder(IMAGE_URI).resize(50, 50).build()
    val output = callbackTransformer.transformRequest(input)
    assertThat(output).isNotSameInstanceAs(input)
    assertThat(output.hasSize()).isFalse()
    val expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter("custom")
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl()
    assertThat(output.uri.toString()).isEqualTo(expected)
  }

  companion object {
    private const val HOST = "http://example.com/"
    private const val KEY = "omgsecretpassword"
    private const val IMAGE = "http://google.com/logo.png"
    private val IMAGE_URI = Uri.parse(IMAGE)
  }
}
