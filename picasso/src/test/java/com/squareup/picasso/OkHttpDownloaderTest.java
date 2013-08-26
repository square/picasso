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
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.OkHttpDownloader.RESPONSE_SOURCE_ANDROID;
import static com.squareup.picasso.OkHttpDownloader.RESPONSE_SOURCE_OKHTTP;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class OkHttpDownloaderTest {
  private static final Uri URL = Uri.parse("/bees.gif");

  private MockWebServer server;
  private OkHttpDownloader loader;

  @Before public void setUp() throws Exception {
    server = new MockWebServer();
    server.play();

    Activity activity = Robolectric.buildActivity(Activity.class).get();
    loader = new OkHttpDownloader(activity) {
      @Override protected HttpURLConnection openConnection(Uri path) throws IOException {
        return (HttpURLConnection) server.getUrl(path.toString()).openConnection();
      }
    };
  }

  @After public void tearDown() throws Exception {
    server.shutdown();
  }

  @Test public void nonTwoHundredReturnsNull() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(302));
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setResponseCode(500));

    assertThat(loader.load(URL, false)).isNull();
    assertThat(loader.load(URL, false)).isNull();
    assertThat(loader.load(URL, false)).isNull();
  }

  @Test public void allowExpiredSetsCacheControl() throws Exception {
    server.enqueue(new MockResponse());
    loader.load(URL, false);
    RecordedRequest request1 = server.takeRequest();
    assertThat(request1.getHeader("Cache-Control")).isNull();

    server.enqueue(new MockResponse());
    loader.load(URL, true);
    RecordedRequest request2 = server.takeRequest();
    assertThat(request2.getHeader("Cache-Control")) //
        .isEqualTo("only-if-cached;max-age=" + Integer.MAX_VALUE);
  }

  @Test public void responseSourceHeaderSetsResponseValue() throws Exception {
    server.enqueue(new MockResponse());
    Downloader.Response response1 = loader.load(URL, false);
    assertThat(response1.cached).isFalse();

    server.enqueue(new MockResponse().addHeader(RESPONSE_SOURCE_ANDROID, "CACHE 200"));
    Downloader.Response response2 = loader.load(URL, true);
    assertThat(response2.cached).isTrue();

    server.enqueue(new MockResponse().addHeader(RESPONSE_SOURCE_OKHTTP, "CACHE 200"));
    Downloader.Response response3 = loader.load(URL, true);
    assertThat(response3.cached).isTrue();
  }
}
