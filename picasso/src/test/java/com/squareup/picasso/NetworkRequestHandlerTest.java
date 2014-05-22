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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockInputStream;
import static com.squareup.picasso.TestUtils.mockNetworkInfo;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
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
    when(downloader.load(any(Uri.class), anyBoolean())).thenReturn(mock(Downloader.Response.class));
  }

  @Test public void doesNotForceLocalCacheOnlyWithAirplaneModeOffAndRetryCount() throws Exception {
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest());
    verify(downloader).load(URI_1, false);
  }

  @Test public void withZeroRetryCountForcesLocalCacheOnly() throws Exception {
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new BitmapHunter(picasso, dispatcher, cache, stats, action, networkHandler);
    hunter.retryCount = 0;
    hunter.hunt();
    verify(downloader).load(URI_1, true);
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
    when(downloader.load(any(Uri.class), anyBoolean())).thenReturn(response);
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest());
    verify(stats).dispatchDownloadFinished(response.contentLength);
  }

  @Test public void unknownContentLengthThrows() throws Exception {
    InputStream stream = mockInputStream();
    Downloader.Response response = new Downloader.Response(stream, false, 0);
    when(downloader.load(any(Uri.class), anyBoolean())).thenReturn(response);
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    try {
      networkHandler.load(action.getRequest());
      fail("Should have thrown IOException.");
    } catch(IOException expected) {
      verifyZeroInteractions(stats);
      verify(stream).close();
    }
  }

  @Test public void cachedResponseDoesNotDispatchToStats() throws Exception {
    Downloader.Response response = new Downloader.Response(mockInputStream(), true, 1024);
    when(downloader.load(any(Uri.class), anyBoolean())).thenReturn(response);
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    networkHandler.load(action.getRequest());
    verifyZeroInteractions(stats);
  }

  @Test public void downloaderCanReturnBitmapDirectly() throws Exception {
    final Bitmap expected = Bitmap.createBitmap(10, 10, ARGB_8888);
    Downloader bitmapDownloader = new Downloader() {
      @Override public Response load(Uri uri, boolean localCacheOnly) throws IOException {
        return new Response(expected, false, 0);
      }
    };
    Action action = TestUtils.mockAction(URI_KEY_1, URI_1);
    NetworkRequestHandler customNetworkHandler = new NetworkRequestHandler(bitmapDownloader, stats);

    Bitmap actual = customNetworkHandler.load(action.getRequest()).getBitmap();
    assertThat(actual).isSameAs(expected);
  }
}
