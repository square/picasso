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
package com.squareup.picasso;

import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.CacheControl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockNetworkInfo;
import static okhttp3.Protocol.HTTP_1_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
public class NetworkRequestHandlerTest {
  private final BlockingDeque<Response> responses = new LinkedBlockingDeque<>();
  private final BlockingDeque<okhttp3.Request> requests = new LinkedBlockingDeque<>();
  private final Downloader downloader = new Downloader() {
    @NonNull @Override public Response load(@NonNull Request request) throws IOException {
      requests.add(request);
      try {
        return responses.takeFirst();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    @Override public void shutdown() {
    }
  };

  @Mock Picasso picasso;
  @Mock Cache cache;
  @Mock Stats stats;
  @Mock Dispatcher dispatcher;
  @Captor ArgumentCaptor<okhttp3.Request> requestCaptor;

  private NetworkRequestHandler networkHandler;

  @Before public void setUp() throws Exception {
    initMocks(this);
    networkHandler = new NetworkRequestHandler(downloader, stats);
  }

  @Test public void doesNotForceLocalCacheOnlyWithAirplaneModeOffAndRetryCount() throws Exception {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest(), 0);
    assertEquals("", requests.takeFirst().cacheControl().toString());
  }

  @Test public void withZeroRetryCountForcesLocalCacheOnly() throws Exception {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new BitmapHunter(picasso, dispatcher, cache, stats, action, networkHandler);
    hunter.retryCount = 0;
    hunter.hunt();
    assertEquals(CacheControl.FORCE_CACHE.toString(), requests.takeFirst().cacheControl().toString());
  }

  @Test public void shouldRetryTwiceWithAirplaneModeOffAndNoNetworkInfo() throws Exception {
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new BitmapHunter(picasso, dispatcher, cache, stats, action, networkHandler);
    assertThat(hunter.shouldRetry(false, null)).isTrue();
    assertThat(hunter.shouldRetry(false, null)).isTrue();
    assertThat(hunter.shouldRetry(false, null)).isFalse();
  }

  @Test public void shouldRetryWithUnknownNetworkInfo() throws Exception {
    assertThat(networkHandler.shouldRetry(false, null)).isTrue();
    assertThat(networkHandler.shouldRetry(true, null)).isTrue();
  }

  @Test public void shouldRetryWithConnectedNetworkInfo() throws Exception {
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnected()).thenReturn(true);
    assertThat(networkHandler.shouldRetry(false, info)).isTrue();
    assertThat(networkHandler.shouldRetry(true, info)).isTrue();
  }

  @Test public void shouldNotRetryWithDisconnectedNetworkInfo() throws Exception {
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(false);
    assertThat(networkHandler.shouldRetry(false, info)).isFalse();
    assertThat(networkHandler.shouldRetry(true, info)).isFalse();
  }

  @Test public void noCacheAndKnownContentLengthDispatchToStats() throws Exception {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest(), 0);
    verify(stats).dispatchDownloadFinished(10);
  }

  @Test public void unknownContentLengthFromDiskThrows() throws Exception {
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
    try {
      networkHandler.load(action.getRequest(), 0);
      fail();
    } catch(IOException expected) {
      verifyZeroInteractions(stats);
      assertTrue(closed.get());
    }
  }

  @Test public void cachedResponseDoesNotDispatchToStats() throws Exception {
    responses.add(responseOf(ResponseBody.create(null, new byte[10]))
        .newBuilder()
        .cacheResponse(responseOf(null))
        .build());
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest(), 0);
    verifyZeroInteractions(stats);
  }

  private static Response responseOf(ResponseBody body) {
    return new Response.Builder()
        .code(200)
        .protocol(HTTP_1_1)
        .request(new okhttp3.Request.Builder().url("http://example.com").build())
        .body(body)
        .build();
  }
}
