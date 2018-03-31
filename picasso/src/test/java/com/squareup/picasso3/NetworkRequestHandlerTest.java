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
package com.squareup.picasso3;

import android.net.NetworkInfo;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.TestUtils.PremadeCall;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso3.TestUtils.URI_1;
import static com.squareup.picasso3.TestUtils.URI_KEY_1;
import static com.squareup.picasso3.TestUtils.mockNetworkInfo;
import static okhttp3.Protocol.HTTP_1_1;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class NetworkRequestHandlerTest {
  final BlockingDeque<Response> responses = new LinkedBlockingDeque<>();
  final BlockingDeque<okhttp3.Request> requests = new LinkedBlockingDeque<>();

  @Mock Picasso picasso;
  @Mock Stats stats;
  @Mock Dispatcher dispatcher;

  private NetworkRequestHandler networkHandler;

  @Before public void setUp() {
    initMocks(this);
    networkHandler = new NetworkRequestHandler(new Call.Factory() {
      @Override public Call newCall(Request request) {
        requests.add(request);
        try {
          return new PremadeCall(request, responses.takeFirst());
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
      }
    }, stats);
  }

  @Test public void doesNotForceLocalCacheOnlyWithAirplaneModeOffAndRetryCount() {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(picasso, action.getRequest(), 0, new RequestHandler.Callback() {
      @Override public void onSuccess(Result result) {
        try {
          assertThat(requests.takeFirst().cacheControl().toString()).isEmpty();
        } catch (InterruptedException e) {
          fail(e.getMessage());
        }
      }

      @Override public void onError(Throwable t) {
        fail(t.getMessage());
      }
    });
  }

  @Test public void withZeroRetryCountForcesLocalCacheOnly() throws Exception {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    PlatformLruCache cache = new PlatformLruCache(0);
    BitmapHunter hunter =
        new BitmapHunter(picasso, dispatcher, cache, stats, action, networkHandler);
    hunter.retryCount = 0;
    hunter.hunt();
    assertThat(requests.takeFirst().cacheControl().toString()).isEqualTo(CacheControl.FORCE_CACHE.toString());
  }

  @Test public void shouldRetryTwiceWithAirplaneModeOffAndNoNetworkInfo() {
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    PlatformLruCache cache = new PlatformLruCache(0);
    BitmapHunter hunter =
        new BitmapHunter(picasso, dispatcher, cache, stats, action, networkHandler);
    assertThat(hunter.shouldRetry(false, null)).isTrue();
    assertThat(hunter.shouldRetry(false, null)).isTrue();
    assertThat(hunter.shouldRetry(false, null)).isFalse();
  }

  @Test public void shouldRetryWithUnknownNetworkInfo() {
    assertThat(networkHandler.shouldRetry(false, null)).isTrue();
    assertThat(networkHandler.shouldRetry(true, null)).isTrue();
  }

  @Test public void shouldRetryWithConnectedNetworkInfo() {
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnected()).thenReturn(true);
    assertThat(networkHandler.shouldRetry(false, info)).isTrue();
    assertThat(networkHandler.shouldRetry(true, info)).isTrue();
  }

  @Test public void shouldNotRetryWithDisconnectedNetworkInfo() {
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(false);
    assertThat(networkHandler.shouldRetry(false, info)).isFalse();
    assertThat(networkHandler.shouldRetry(true, info)).isFalse();
  }

  @Test public void noCacheAndKnownContentLengthDispatchToStats() {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(picasso, action.getRequest(), 0, new RequestHandler.Callback() {
      @Override public void onSuccess(Result result) {
        verify(stats).dispatchDownloadFinished(10);
      }

      @Override public void onError(Throwable t) {
        fail(t.getMessage());
      }
    });
  }

  @Test public void unknownContentLengthFromDiskThrows() {
    final AtomicBoolean closed = new AtomicBoolean();
    ResponseBody body = new ResponseBody() {
      @Override public MediaType contentType() { return null; }
      @Override public long contentLength() { return 0; }
      @Override public BufferedSource source() { return new Buffer(); }
      @Override public void close() {
        closed.set(true);
        super.close();
      }
    };
    responses.add(responseOf(body)
        .newBuilder()
        .cacheResponse(responseOf(null))
        .build());
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(picasso, action.getRequest(), 0, new RequestHandler.Callback() {
      @Override public void onSuccess(Result result) {
        fail();
      }

      @Override public void onError(Throwable t) {
        verifyZeroInteractions(stats);
        assertTrue(closed.get());
      }
    });
  }

  @Test public void cachedResponseDoesNotDispatchToStats() {
    responses.add(responseOf(ResponseBody.create(null, new byte[10]))
        .newBuilder()
        .cacheResponse(responseOf(null))
        .build());
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(picasso, action.getRequest(), 0, new RequestHandler.Callback() {
      @Override public void onSuccess(Result result) {
        verifyZeroInteractions(stats);
      }

      @Override public void onError(Throwable t) {
        fail(t.getMessage());
      }
    });
  }

  private static Response responseOf(ResponseBody body) {
    return new Response.Builder()
        .code(200)
        .protocol(HTTP_1_1)
        .request(new okhttp3.Request.Builder().url("http://example.com").build())
        .message("OK")
        .body(body)
        .build();
  }
}
