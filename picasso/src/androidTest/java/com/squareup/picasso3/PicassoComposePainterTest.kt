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
import com.squareup.picasso3.compose.rememberPainter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PicassoComposePainterTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun firstFrameConsumesStateFromLayout() {
        lateinit var lastRequest: Request
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val picasso = Picasso.Builder(context)
            .addRequestHandler(object : RequestHandler() {
                override fun canHandleRequest(data: Request): Boolean = true
                override fun load(picasso: Picasso, request: Request, callback: Callback) {
                    lastRequest = request
                }
            })
            .build()
        var size: IntSize by mutableStateOf(IntSize.Zero)
        var initialFrame = true

        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val painter = picasso.rememberPainter {
                    it.load("http://example.com/")
                        .addHeader("width", size.width.toString())
                        .addHeader("height", size.height.toString())
                }
                Canvas(
                    Modifier
                        .requiredSize(9.dp)
                        .onSizeChanged { size = it }
                ) {
                    val canvasSize = this.size

                    if (initialFrame) {
                        initialFrame = false
                        // Initial request was made with uninitialized size.
                        assertThat(lastRequest.headers?.toMultimap()).containsAtLeastEntriesIn(
                            mapOf(
                                "width" to listOf("0"),
                                "height" to listOf("0"),
                            )
                        )
                    }

                    with(painter) {
                        draw(canvasSize)
                    }

                    // Draw triggers request was made with size.
                    assertThat(lastRequest.headers?.toMultimap()).containsAtLeastEntriesIn(
                        mapOf(
                            "width" to listOf("9"),
                            "height" to listOf("9"),
                        )
                    )
                }
            }
        }
    }

    @Test
    fun redrawDoesNotReexecuteUnchangedRequest() {
        var requestCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val picasso = Picasso.Builder(context)
            .addRequestHandler(object : RequestHandler() {
                override fun canHandleRequest(data: Request): Boolean = true
                override fun load(picasso: Picasso, request: Request, callback: Callback) {
                    requestCount++
                }
            })
            .build()
        var drawInvalidator by mutableStateOf(0)

        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val painter = picasso.rememberPainter {
                    it.load("http://example.com/")
                }
                Canvas(Modifier.fillMaxSize()) {
                    println(drawInvalidator)
                    val canvasSize = this.size
                    with(painter) {
                        draw(canvasSize)
                    }
                }
            }
        }

        rule.runOnIdle {
            assertThat(requestCount).isEqualTo(1)
        }

        drawInvalidator++

        rule.runOnIdle {
            assertThat(requestCount).isEqualTo(1)
        }
    }

    @Test
    fun newRequestLoaded_whenRequestDependenciesChangedAfterFirstFrame() {
        lateinit var lastRequest: Request
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val picasso = Picasso.Builder(context)
            .addRequestHandler(object : RequestHandler() {
                override fun canHandleRequest(data: Request): Boolean = true
                override fun load(picasso: Picasso, request: Request, callback: Callback) {
                    lastRequest = request
                }
            })
            .build()
        var testHeader by mutableStateOf("one")

        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val painter = picasso.rememberPainter {
                    it.load("http://example.com/")
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

        rule.runOnIdle {
            assertThat(lastRequest.headers?.get("testHeader")).isEqualTo("one")
        }

        testHeader = "two"

        rule.runOnIdle {
            assertThat(lastRequest.headers?.get("testHeader")).isEqualTo("two")
        }

        testHeader = "three"

        rule.runOnIdle {
            assertThat(lastRequest.headers?.get("testHeader")).isEqualTo("three")
        }
    }
}
