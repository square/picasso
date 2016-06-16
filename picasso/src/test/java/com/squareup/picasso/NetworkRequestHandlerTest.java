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

import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockInputStream;
import static com.squareup.picasso.TestUtils.mockNetworkInfo;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
public class NetworkRequestHandlerTest {

  @Mock Picasso picasso;
  @Mock Cache cache;
  @Mock Stats stats;
  @Mock Dispatcher dispatcher;
  @Mock Downloader downloader;
  NetworkRequestHandler networkHandler;

  @Before public void setUp() throws Exception {
    initMocks(this);
    networkHandler = new NetworkRequestHandler(downloader, stats);
    when(downloader.load(any(Uri.class), anyInt())).thenReturn(mock(Downloader.Response.class));
  }

  @Test public void doesNotForceLocalCacheOnlyWithAirplaneModeOffAndRetryCount() throws Exception {
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest(), 0);
    verify(downloader).load(URI_1, 0);
  }

  @Test public void withZeroRetryCountForcesLocalCacheOnly() throws Exception {
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new BitmapHunter(picasso, dispatcher, cache, stats, action, networkHandler);
    hunter.retryCount = 0;
    hunter.hunt();
    verify(downloader).load(URI_1, NetworkPolicy.OFFLINE.index);
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
    Downloader.Response response = new Downloader.Response(mockInputStream(), false, 1024);
    when(downloader.load(any(Uri.class), anyInt())).thenReturn(response);
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest(), 0);
    verify(stats).dispatchDownloadFinished(response.contentLength);
  }

  @Test public void unknownContentLengthFromDiskThrows() throws Exception {
    InputStream stream = mockInputStream();
    Downloader.Response response = new Downloader.Response(stream, true, 0);
    when(downloader.load(any(Uri.class), anyInt())).thenReturn(response);
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    try {
      networkHandler.load(action.getRequest(), 0);
      fail("Should have thrown IOException.");
    } catch(IOException expected) {
      verifyZeroInteractions(stats);
      verify(stream).close();
    }
  }

  @Test public void cachedResponseDoesNotDispatchToStats() throws Exception {
    Downloader.Response response = new Downloader.Response(mockInputStream(), true, 1024);
    when(downloader.load(any(Uri.class), anyInt())).thenReturn(response);
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest(), 0);
    verifyZeroInteractions(stats);
  }

  @Test public void downloaderCanReturnBitmapDirectly() throws Exception {
    final Bitmap expected = Bitmap.createBitmap(10, 10, ARGB_8888);
    Downloader bitmapDownloader = new Downloader() {
      @Override public Response load(@NonNull Uri uri, int networkPolicy) throws IOException {
        return new Response(expected, false);
      }

      @Override public void shutdown() {
      }
    };
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    NetworkRequestHandler customNetworkHandler = new NetworkRequestHandler(bitmapDownloader, stats);

    RequestHandler.Result result = customNetworkHandler.load(action.getRequest(), 0);
    assertThat(result.getBitmap()).isSameAs(expected);
    assertThat(result.getStream()).isNull();
  }

  @Test public void downloaderInputStreamNotDecoded() throws Exception {
    final InputStream is = new ByteArrayInputStream(new byte[] { 'a' });
    Downloader bitmapDownloader = new Downloader() {
      @Override public Response load(@NonNull Uri uri, int networkPolicy) throws IOException {
        return new Response(is, false, 1);
      }

      @Override public void shutdown() {
      }
    };
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    NetworkRequestHandler customNetworkHandler = new NetworkRequestHandler(bitmapDownloader, stats);

    RequestHandler.Result result = customNetworkHandler.load(action.getRequest(), 0);
    assertThat(result.getStream()).isSameAs(is);
    assertThat(result.getBitmap()).isNull();
  }
}
