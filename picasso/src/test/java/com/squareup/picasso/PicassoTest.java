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
import android.widget.ImageView;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Picasso.Listener;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockCanceledRequest;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockRequest;
import static com.squareup.picasso.TestUtils.mockTarget;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PicassoTest {

  @Mock Context context;
  @Mock Downloader downloader;
  @Mock Dispatcher dispatcher;
  @Mock Cache cache;
  @Mock Listener listener;
  @Mock Stats stats;

  private Picasso picasso;

  @Before public void setUp() {
    initMocks(this);
    picasso = new Picasso(context, dispatcher, cache, listener, stats, false);
  }

  @Test public void submitWithNullTargetInvokesDispatcher() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1, null);
    picasso.enqueueAndSubmit(request);
    assertThat(picasso.targetToRequest).isEmpty();
    verify(dispatcher).dispatchSubmit(request);
  }

  @Test public void submitWithTargetInvokesDispatcher() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    assertThat(picasso.targetToRequest).isEmpty();
    picasso.enqueueAndSubmit(request);
    assertThat(picasso.targetToRequest).hasSize(1);
    verify(dispatcher).dispatchSubmit(request);
  }

  @Test public void quickMemoryCheckReturnsBitmapIfInCache() throws Exception {
    when(cache.get(URI_KEY_1)).thenReturn(BITMAP_1);
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    assertThat(cached).isEqualTo(BITMAP_1);
    verify(stats).dispatchCacheHit();
  }

  @Test public void completeInvokesSuccessOnAllSuccessfulRequests() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockCanceledRequest();
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.getRequests()).thenReturn(Arrays.asList(request1, request2));
    when(hunter.getLoadedFrom()).thenReturn(MEMORY);
    picasso.complete(hunter);
    verify(request1).complete(BITMAP_1, MEMORY);
    verify(request2, never()).complete(eq(BITMAP_1), any(Picasso.LoadedFrom.class));
  }

  @Test public void completeInvokesErrorOnAllFailedRequests() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockCanceledRequest();
    BitmapHunter hunter = mockHunter(URI_KEY_1, null, false);
    when(hunter.getRequests()).thenReturn(Arrays.asList(request1, request2));
    picasso.complete(hunter);
    verify(request1).error();
    verify(request2, never()).error();
  }

  @Test public void cancelExistingRequestWithUnknownTarget() throws Exception {
    ImageView target = mockImageViewTarget();
    Request request = mockRequest(URI_KEY_1, URI_1, target);
    picasso.cancelRequest(target);
    verifyZeroInteractions(request);
    verifyZeroInteractions(dispatcher);
  }

  @Test public void cancelExistingRequestWithImageViewTarget() throws Exception {
    ImageView target = mockImageViewTarget();
    Request request = mockRequest(URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(request);
    assertThat(picasso.targetToRequest).hasSize(1);
    picasso.cancelRequest(target);
    assertThat(picasso.targetToRequest).isEmpty();
    verify(request).cancel();
    verify(dispatcher).dispatchCancel(request);
  }

  @Test public void cancelExistingRequestWithTarget() throws Exception {
    Target target = mockTarget();
    Request request = mockRequest(URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(request);
    assertThat(picasso.targetToRequest).hasSize(1);
    picasso.cancelRequest(target);
    assertThat(picasso.targetToRequest).isEmpty();
    verify(request).cancel();
    verify(dispatcher).dispatchCancel(request);
  }

  @Test public void shutdown() throws Exception {
    picasso.shutdown();
    verify(cache).clear();
    verify(stats).shutdown();
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
  }

  @Test public void shutdownTwice() throws Exception {
    picasso.shutdown();
    picasso.shutdown();
    verify(cache).clear();
    verify(stats).shutdown();
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
  }

  @Test public void loadThrowsWithInvalidInput() throws Exception {
    try {
      picasso.load("");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load("      ");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load(0);
      fail("Zero resourceId should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void builderInvalidListener() throws Exception {
    try {
      new Picasso.Builder(context).listener(null);
      fail("Null listener should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).listener(listener).listener(listener);
      fail("Setting Listener twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidLoader() throws Exception {
    try {
      new Picasso.Builder(context).downloader(null);
      fail("Null Downloader should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).downloader(downloader).downloader(downloader);
      fail("Setting Downloader twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidExecutor() throws Exception {
    try {
      new Picasso.Builder(context).executor(null);
      fail("Null Executor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      ExecutorService executor = mock(ExecutorService.class);
      new Picasso.Builder(context).executor(executor).executor(executor);
      fail("Setting Executor twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidCache() throws Exception {
    try {
      new Picasso.Builder(context).memoryCache(null);
      fail("Null Cache should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).memoryCache(cache).memoryCache(cache);
      fail("Setting Cache twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }
}
