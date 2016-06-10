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

import android.net.Uri;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Okio;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(RobolectricGradleTestRunner.class)
@Config(shadows = { Shadows.ShadowNetwork.class })
public class OkHttpDownloaderTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public MockWebServer server = new MockWebServer();

  private OkHttpDownloader downloader;
  private Uri uri;

  @Before public void setUp() throws Exception {
    downloader = new OkHttpDownloader(temporaryFolder.getRoot());
    uri = Uri.parse(server.url("/").toString());
  }

  @Test public void cachedResponse() throws Exception {
    server.enqueue(new MockResponse()
        .setHeader("Cache-Control", "max-age=31536000")
        .setBody("Hi"));

    Downloader.Response response1 = downloader.load(uri, 0);
    assertThat(response1.cached).isFalse();
    // Exhaust input stream to ensure response is cached.
    Okio.buffer(Okio.source(response1.getInputStream())).readByteArray();

    Downloader.Response response2 = downloader.load(uri, NetworkPolicy.OFFLINE.index);
    assertThat(response2.cached).isTrue();
  }

  @Test public void offlineStaleResponse() throws Exception {
    server.enqueue(new MockResponse()
        .setHeader("Cache-Control", "max-age=1")
        .setHeader("Expires", "Mon, 29 Dec 2014 21:44:55 GMT")
        .setBody("Hi"));

    Downloader.Response response1 = downloader.load(uri, 0);
    assertThat(response1.cached).isFalse();
    // Exhaust input stream to ensure response is cached.
    Okio.buffer(Okio.source(response1.getInputStream())).readByteArray();

    Downloader.Response response2 = downloader.load(uri, NetworkPolicy.OFFLINE.index);
    assertThat(response2.cached).isTrue();
  }

  @Test public void networkPolicyNoCache() throws Exception {
    MockResponse response =
        new MockResponse().setHeader("Cache-Control", "max-age=31536000").setBody("Hi");
    server.enqueue(response);

    Downloader.Response response1 = downloader.load(uri, 0);
    assertThat(response1.cached).isFalse();
    // Exhaust input stream to ensure response is cached.
    Okio.buffer(Okio.source(response1.getInputStream())).readByteArray();

    // Enqueue the same response again but this time use NetworkPolicy.NO_CACHE.
    server.enqueue(response);

    Downloader.Response response2 = downloader.load(uri, NetworkPolicy.NO_CACHE.index);
    // Response should not be coming from cache even if it was cached previously.
    assertThat(response2.cached).isFalse();
  }

  @Test public void networkPolicyNoStore() throws Exception {
    server.enqueue(new MockResponse());
    downloader.load(uri, NetworkPolicy.NO_STORE.index);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test public void readsContentLengthHeader() throws Exception {
    server.enqueue(new MockResponse().addHeader("Content-Length", 1024));

    Downloader.Response response = downloader.load(uri, 0);
    assertThat(response.contentLength).isEqualTo(1024);
  }

  @Test public void throwsResponseException() throws Exception {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 401 Not Authorized"));

    try {
      downloader.load(uri, 0);
      fail("Expected ResponseException.");
    } catch (Downloader.ResponseException e) {
      assertThat(e).hasMessage("401 Not Authorized");
    }
  }

  @Test public void shutdownClosesCacheIfNotShared() throws Exception {
    OkHttpDownloader downloader = new OkHttpDownloader(temporaryFolder.getRoot());
    Cache cache = downloader.getClient().getCache();
    downloader.shutdown();
    assertThat(cache.isClosed()).isTrue();
  }

  @Test public void shutdownDoesNotCloseCacheIfShared() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Cache cache = new Cache(temporaryFolder.getRoot(), 100);
    client.setCache(cache);
    new OkHttpDownloader(client).shutdown();
    assertThat(cache.isClosed()).isFalse();
  }
}
