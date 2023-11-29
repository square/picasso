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
package com.squareup.picasso3.compose

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Picasso.LoadedFrom
import com.squareup.picasso3.Request
import com.squareup.picasso3.RequestHandler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.Dispatchers

@RunWith(AndroidJUnit4::class)
class PicassoPainterTest {

  @get:Rule
  val rule = createComposeRule()

  @Test
  fun firstFrameConsumesStateFromLayout() {
    lateinit var lastRequest: Request
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    val picasso = Picasso.Builder(context)
      .callFactory { throw RuntimeException() }
      .dispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
      .addRequestHandler(object : RequestHandler() {
        override fun canHandleRequest(data: Request): Boolean = true
        override fun load(picasso: Picasso, request: Request, callback: Callback) {
          lastRequest = request
          callback.onSuccess(Result.Bitmap(Bitmap.createBitmap(1, 1, ARGB_8888), LoadedFrom.MEMORY))
        }
      })
      .build()
    var size: IntSize by mutableStateOf(IntSize.Zero)
    var drawn = false

    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val painter = picasso.rememberPainter {
          it.load("http://example.com/")
            // Headers are not part of a cache key, using a stable key to break cache
            .stableKey("http://example.com/$size")
            .addHeader("width", size.width.toString())
            .addHeader("height", size.height.toString())
        }
        Canvas(
          Modifier
            .requiredSize(9.dp)
            .onSizeChanged { size = it }
        ) {
          val canvasSize = this.size

          with(painter) {
            draw(canvasSize)
          }
          drawn = true
        }
      }
    }

    rule.waitUntil { drawn }

    // Draw triggers request was made with size.
    assertThat(lastRequest.headers?.toMultimap()).containsAtLeastEntriesIn(
      mapOf(
        "width" to listOf("9"),
        "height" to listOf("9")
      )
    )
  }

  @Test
  fun redrawDoesNotReexecuteUnchangedRequest() {
    var requestCount = 0
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val picasso = Picasso.Builder(context)
      .callFactory { throw RuntimeException() }
      .dispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
      .addRequestHandler(object : RequestHandler() {
        override fun canHandleRequest(data: Request): Boolean = true
        override fun load(picasso: Picasso, request: Request, callback: Callback) {
          requestCount++
          callback.onSuccess(Result.Bitmap(Bitmap.createBitmap(1, 1, ARGB_8888), LoadedFrom.MEMORY))
        }
      })
      .build()

    var drawInvalidator by mutableStateOf(0)
    var drawCount = 0
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val painter = picasso.rememberPainter {
          it.load("http://example.com/")
        }
        Canvas(Modifier.fillMaxSize()) {
          drawCount++
          drawInvalidator = 1

          val canvasSize = this.size
          with(painter) {
            draw(canvasSize)
          }
        }
      }
    }

    rule.waitUntil { drawCount == 2 }
    assertThat(requestCount).isEqualTo(1)
  }

  @Test
  fun newRequestLoaded_whenRequestDependenciesChangedAfterFirstFrame() {
    var lastRequest: Request? = null
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val picasso = Picasso.Builder(context)
      .callFactory { throw RuntimeException() }
      .dispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
      .addRequestHandler(object : RequestHandler() {
        override fun canHandleRequest(data: Request): Boolean = true
        override fun load(picasso: Picasso, request: Request, callback: Callback) {
          lastRequest = request
          callback.onSuccess(Result.Bitmap(Bitmap.createBitmap(1, 1, ARGB_8888), LoadedFrom.MEMORY))
        }
      })
      .build()
    var testHeader by mutableStateOf("one")

    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val painter = picasso.rememberPainter {
          it.load("http://example.com/")
            // Headers are not part of a cache key, using a stable key to break cache
            .stableKey("http://example.com/$testHeader")
            .addHeader("testHeader", testHeader)
        }
        Canvas(Modifier.fillMaxSize()) {
          val canvasSize = this.size

          with(painter) {
            draw(canvasSize)
          }
        }
      }
    }

    rule.waitUntil { lastRequest != null }
    assertThat(lastRequest!!.headers?.get("testHeader")).isEqualTo("one")

    var currentRequest = lastRequest
    testHeader = "two"

    // On API 21 runOnIdle runs before the composition recomposes :-(
    // Waiting until the request updates, then asserting
    rule.waitUntil { currentRequest != lastRequest }
    assertThat(lastRequest!!.headers?.get("testHeader")).isEqualTo("two")

    currentRequest = lastRequest
    testHeader = "three"

    rule.waitUntil { currentRequest != lastRequest }
    assertThat(lastRequest!!.headers?.get("testHeader")).isEqualTo("three")
  }
}
