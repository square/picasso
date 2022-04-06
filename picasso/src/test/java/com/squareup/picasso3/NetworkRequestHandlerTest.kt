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

import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.RequestHandler.Result
import com.squareup.picasso3.TestUtils.CUSTOM_HEADER_NAME
import com.squareup.picasso3.TestUtils.CUSTOM_HEADER_VALUE
import com.squareup.picasso3.TestUtils.EventRecorder
import com.squareup.picasso3.TestUtils.PremadeCall
import com.squareup.picasso3.TestUtils.URI_1
import com.squareup.picasso3.TestUtils.URI_KEY_1
import com.squareup.picasso3.TestUtils.mockNetworkInfo
import com.squareup.picasso3.TestUtils.mockPicasso
import okhttp3.CacheControl
import okhttp3.MediaType
import okhttp3.Protocol.HTTP_1_1
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.initMocks
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class NetworkRequestHandlerTest {
  private val responses = LinkedBlockingDeque<Response>()
  private val requests = LinkedBlockingDeque<okhttp3.Request>()

  @Mock internal lateinit var dispatcher: Dispatcher
  private lateinit var picasso: Picasso
  private lateinit var networkHandler: NetworkRequestHandler

  @Before fun setUp() {
    initMocks(this)
    picasso = mockPicasso(RuntimeEnvironment.application)
    networkHandler = NetworkRequestHandler { request ->
      requests.add(request)
      try {
        PremadeCall(request, responses.takeFirst())
      } catch (e: InterruptedException) {
        throw AssertionError(e)
      }
    }
  }

  @Test fun doesNotForceLocalCacheOnlyWithAirplaneModeOffAndRetryCount() {
    responses.add(responseOf(ByteArray(10).toResponseBody(null)))
    val action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1)
    val latch = CountDownLatch(1)
    networkHandler.load(
      picasso = picasso,
      request = action.request,
      callback = object : RequestHandler.Callback {
        override fun onSuccess(result: Result?) {
          try {
            assertThat(requests.takeFirst().cacheControl.toString()).isEmpty()
            latch.countDown()
          } catch (e: InterruptedException) {
            throw AssertionError(e)
          }
        }

        override fun onError(t: Throwable): Unit = throw AssertionError(t)
      }
    )
    assertThat(latch.await(10, SECONDS)).isTrue()
  }

  @Test fun withZeroRetryCountForcesLocalCacheOnly() {
    responses.add(responseOf(ByteArray(10).toResponseBody(null)))
    val action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1)
    val cache = PlatformLruCache(0)
    val hunter = BitmapHunter(picasso, dispatcher, cache, action, networkHandler)
    hunter.retryCount = 0
    hunter.hunt()
    assertThat(requests.takeFirst().cacheControl.toString())
      .isEqualTo(CacheControl.FORCE_CACHE.toString())
  }

  @Test fun shouldRetryTwiceWithAirplaneModeOffAndNoNetworkInfo() {
    val action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1)
    val cache = PlatformLruCache(0)
    val hunter = BitmapHunter(picasso, dispatcher, cache, action, networkHandler)
    assertThat(hunter.shouldRetry(airplaneMode = false, info = null)).isTrue()
    assertThat(hunter.shouldRetry(airplaneMode = false, info = null)).isTrue()
    assertThat(hunter.shouldRetry(airplaneMode = false, info = null)).isFalse()
  }

  @Test fun shouldRetryWithUnknownNetworkInfo() {
    assertThat(networkHandler.shouldRetry(airplaneMode = false, info = null)).isTrue()
    assertThat(networkHandler.shouldRetry(airplaneMode = true, info = null)).isTrue()
  }

  @Test fun shouldRetryWithConnectedNetworkInfo() {
    val info = mockNetworkInfo()
    `when`(info.isConnected).thenReturn(true)
    assertThat(networkHandler.shouldRetry(airplaneMode = false, info = info)).isTrue()
    assertThat(networkHandler.shouldRetry(airplaneMode = true, info = info)).isTrue()
  }

  @Test fun shouldNotRetryWithDisconnectedNetworkInfo() {
    val info = mockNetworkInfo()
    `when`(info.isConnectedOrConnecting).thenReturn(false)
    assertThat(networkHandler.shouldRetry(airplaneMode = false, info = info)).isFalse()
    assertThat(networkHandler.shouldRetry(airplaneMode = true, info = info)).isFalse()
  }

  @Test fun noCacheAndKnownContentLengthDispatchToStats() {
    val eventRecorder = EventRecorder()
    val picasso = picasso.newBuilder().addEventListener(eventRecorder).build()
    val knownContentLengthSize = 10
    responses.add(responseOf(ByteArray(knownContentLengthSize).toResponseBody(null)))
    val action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1)
    val latch = CountDownLatch(1)
    networkHandler.load(
      picasso = picasso,
      request = action.request,
      callback = object : RequestHandler.Callback {
        override fun onSuccess(result: Result?) {
          assertThat(eventRecorder.downloadSize).isEqualTo(knownContentLengthSize)
          latch.countDown()
        }

        override fun onError(t: Throwable): Unit = throw AssertionError(t)
      }
    )
    assertThat(latch.await(10, SECONDS)).isTrue()
  }

  @Test fun unknownContentLengthFromDiskThrows() {
    val eventRecorder = EventRecorder()
    val picasso = picasso.newBuilder().addEventListener(eventRecorder).build()
    val closed = AtomicBoolean()
    val body = object : ResponseBody() {
      override fun contentType(): MediaType? = null
      override fun contentLength(): Long = 0
      override fun source(): BufferedSource = Buffer()
      override fun close() {
        closed.set(true)
        super.close()
      }
    }
    responses += responseOf(body)
      .newBuilder()
      .cacheResponse(responseOf(null))
      .build()
    val action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1)
    val latch = CountDownLatch(1)
    networkHandler.load(
      picasso = picasso,
      request = action.request,
      callback = object : RequestHandler.Callback {
        override fun onSuccess(result: Result?): Unit = throw AssertionError()

        override fun onError(t: Throwable) {
          assertThat(eventRecorder.downloadSize).isEqualTo(0)
          assertTrue(closed.get())
          latch.countDown()
        }
      }
    )
    assertThat(latch.await(10, SECONDS)).isTrue()
  }

  @Test fun cachedResponseDoesNotDispatchToStats() {
    val eventRecorder = EventRecorder()
    val picasso = picasso.newBuilder().addEventListener(eventRecorder).build()
    responses += responseOf(ByteArray(10).toResponseBody(null))
      .newBuilder()
      .cacheResponse(responseOf(null))
      .build()
    val action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1)
    val latch = CountDownLatch(1)
    networkHandler.load(
      picasso = picasso,
      request = action.request,
      callback = object : RequestHandler.Callback {
        override fun onSuccess(result: Result?) {
          assertThat(eventRecorder.downloadSize).isEqualTo(0)
          latch.countDown()
        }

        override fun onError(t: Throwable): Unit = throw AssertionError(t)
      }
    )
    assertThat(latch.await(10, SECONDS)).isTrue()
  }

  @Test fun customHeaders() {
    responses += responseOf(ByteArray(10).toResponseBody(null))
      .newBuilder()
      .cacheResponse(responseOf(null))
      .build()
    val action = TestUtils.mockAction(
      picasso,
      key = URI_KEY_1,
      uri = URI_1,
      headers = mapOf(CUSTOM_HEADER_NAME to CUSTOM_HEADER_VALUE)
    )
    val latch = CountDownLatch(1)
    networkHandler.load(
      picasso = picasso,
      request = action.request,
      callback = object : RequestHandler.Callback {
        override fun onSuccess(result: Result?) {
          with(requests.first.headers) {
            assertThat(names()).containsExactly(CUSTOM_HEADER_NAME)
            assertThat(values(CUSTOM_HEADER_NAME)).containsExactly(CUSTOM_HEADER_VALUE)
          }
          latch.countDown()
        }

        override fun onError(t: Throwable): Unit = throw AssertionError(t)
      }
    )
    assertThat(latch.await(10, SECONDS)).isTrue()
  }

  @Test fun shouldHandleSchemeInsensitiveCase() {
    val schemes = arrayOf("http", "https", "HTTP", "HTTPS", "HTtP")
    for (scheme in schemes) {
      val uri = URI_1.buildUpon().scheme(scheme).build()
      assertThat(networkHandler.canHandleRequest(TestUtils.mockRequest(uri))).isTrue()
    }
  }

  private fun responseOf(body: ResponseBody?) =
    Response.Builder()
      .code(200)
      .protocol(HTTP_1_1)
      .request(okhttp3.Request.Builder().url("http://example.com").build())
      .message("OK")
      .body(body)
      .build()
}
