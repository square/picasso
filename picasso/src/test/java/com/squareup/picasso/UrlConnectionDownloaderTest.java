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

import android.app.Activity;
import android.net.Uri;
import android.net.http.HttpResponseCache;
import java.io.IOException;
import java.net.HttpURLConnection;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class UrlConnectionDownloaderTest {
  private static final Uri URL = Uri.parse("/bees.gif");

  private UrlConnectionDownloader loader;

  @Rule public MockWebServer server = new MockWebServer();

  @Before public void setUp() throws Exception {
    Activity activity = Robolectric.buildActivity(Activity.class).get();
    loader = new UrlConnectionDownloader(activity) {
      @Override protected HttpURLConnection openConnection(Uri path) throws IOException {
        return (HttpURLConnection) server.url(path.toString()).url().openConnection();
      }
    };
  }

  @Test public void cacheOnlyInstalledOnce() throws Exception {
    UrlConnectionDownloader.cache = null;

    server.enqueue(new MockResponse());
    loader.load(URL, 0);
    Object cache = UrlConnectionDownloader.cache;
    assertThat(cache).isNotNull();

    server.enqueue(new MockResponse());
    loader.load(URL, 0);
    assertThat(UrlConnectionDownloader.cache).isSameAs(cache);
  }

  @Test public void shutdownClosesCache() throws Exception {
    HttpResponseCache cache = mock(HttpResponseCache.class);
    UrlConnectionDownloader.cache = cache;
    loader.shutdown();
    verify(cache).close();
  }

  @Test public void networkPolicyNoCache() throws Exception {
    server.enqueue(new MockResponse());
    loader.load(URL, NetworkPolicy.NO_CACHE.index);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test public void networkPolicyNoStore() throws Exception {
    server.enqueue(new MockResponse());
    loader.load(URL, NetworkPolicy.NO_STORE.index);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test public void networkPolicyNoCacheNoStore() throws Exception {
    int networkPolicy = 0;
    networkPolicy |= MemoryPolicy.NO_CACHE.index;
    networkPolicy |= MemoryPolicy.NO_STORE.index;

    server.enqueue(new MockResponse());
    loader.load(URL, networkPolicy);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("Cache-Control")).isEqualTo("no-cache,no-store");
  }

  @Test public void readsContentLengthHeader() throws Exception {
    server.enqueue(new MockResponse().addHeader("Content-Length", 1024));
    Downloader.Response response = loader.load(URL, 0);
    assertThat(response.contentLength).isEqualTo(1024);
  }

  @Test public void throwsResponseException() throws Exception {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 401 Not Authorized"));
    try {
      loader.load(URL, 0);
      fail("Expected ResponseException.");
    } catch (Downloader.ResponseException e) {
      assertThat(e).hasMessage("401 Not Authorized");
    }
  }
}
