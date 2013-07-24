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
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockRequest;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NetworkBitmapHunterTest {

  @Mock Context context;
  @Mock Picasso picasso;
  @Mock Cache cache;
  @Mock Dispatcher dispatcher;
  @Mock Downloader downloader;

  @Before public void setUp() throws Exception {
    initMocks(this);
    when(downloader.load(any(Uri.class), anyBoolean())).thenReturn(mock(Downloader.Response.class));
  }

  @Test public void doesNotForceLocalCacheOnlyWithAirplaneModeOffAndRetryCount() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    NetworkBitmapHunter hunter =
        new NetworkBitmapHunter(picasso, dispatcher, cache, request, downloader, false);
    hunter.decode(request.getUri(), null, 2);
    verify(downloader).load(URI_1, false);
  }

  @Test public void withZeroRetryCountForcesLocalCacheOnly() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    NetworkBitmapHunter hunter =
        new NetworkBitmapHunter(picasso, dispatcher, cache, request, downloader, false);
    hunter.decode(request.getUri(), null, 0);
    verify(downloader).load(URI_1, true);
  }

  @Test public void airplaneModeForcesLocalCacheOnly() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    NetworkBitmapHunter hunter =
        new NetworkBitmapHunter(picasso, dispatcher, cache, request, downloader, true);
    hunter.decode(request.getUri(), null, hunter.retryCount);
    verify(downloader).load(URI_1, true);
  }
}
