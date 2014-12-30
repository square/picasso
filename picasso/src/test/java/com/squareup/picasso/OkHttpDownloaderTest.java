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
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.rule.MockWebServerRule;
import okio.Okio;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class OkHttpDownloaderTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public MockWebServerRule server = new MockWebServerRule();

  private OkHttpDownloader downloader;
  private Uri uri;

  @Before public void setUp() throws Exception {
    downloader = new OkHttpDownloader(temporaryFolder.getRoot());
    uri = Uri.parse(server.getUrl("/").toString());
  }

  @Test public void responseSourceHeaderSetsResponseValue() throws Exception {
    server.enqueue(new MockResponse()
        .setHeader("Cache-Control", "max-age=31536000")
        .setBody("Hi"));

    Downloader.Response response1 = downloader.load(uri, false);
    assertThat(response1.cached).isFalse();
    // Exhaust input stream to ensure response is cached.
    Okio.buffer(Okio.source(response1.getInputStream())).readByteArray();

    Downloader.Response response2 = downloader.load(uri, true);
    assertThat(response2.cached).isTrue();
  }

  @Test public void readsContentLengthHeader() throws Exception {
    server.enqueue(new MockResponse().addHeader("Content-Length", 1024));

    Downloader.Response response = downloader.load(uri, false);
    assertThat(response.contentLength).isEqualTo(1024);
  }

  @Test public void throwsResponseException() throws Exception {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 401 Not Authorized"));

    try {
      downloader.load(uri, false);
      fail("Expected ResponseException.");
    } catch (Downloader.ResponseException e) {
      assertThat(e).hasMessage("401 Not Authorized");
    }
  }

  @Test public void shutdownClosesCache() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Cache cache = new Cache(temporaryFolder.getRoot(), 100);
    client.setCache(cache);
    new OkHttpDownloader(client).shutdown();
    assertThat(cache.isClosed()).isTrue();
  }
}
