package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import static com.squareup.picasso.Picasso.Listener;
import static com.squareup.picasso.Request.LoadedFrom;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockCanceledRequest;
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
    picasso = new Picasso(context, downloader, dispatcher, cache, listener, stats, false);
  }

  @Test public void submitWithNullTargetSkips() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1, null);
    picasso.submit(request);
    assertThat(picasso.targetToRequest).isEmpty();
    verifyZeroInteractions(dispatcher);
  }

  @Test public void submitWithTargetInvokesDispatcher() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    picasso.submit(request);
    assertThat(picasso.targetToRequest).hasSize(1);
    verify(dispatcher).dispatchSubmit(request);
  }

  @Test public void quickMemoryCheckReturnsBitmapIfInCache() throws Exception {
    when(cache.get(URI_KEY_1)).thenReturn(BITMAP_1);
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    assertThat(cached).isEqualTo(BITMAP_1);
    verify(stats).cacheHit();
  }

  @Test public void quickMemoryCheckReturnsNullIfNotInCache() throws Exception {
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    assertThat(cached).isNull();
    verifyZeroInteractions(stats);
  }

  @Test public void completeInvokesAllNonCanceledRequests() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockCanceledRequest();
    List<Request> list = Arrays.asList(request1, request2);
    picasso.complete(list, BITMAP_1, LoadedFrom.MEMORY);
    verify(request1).complete(BITMAP_1, LoadedFrom.MEMORY);
    verify(request2, never()).complete(eq(BITMAP_1), any(LoadedFrom.class));
  }

  @Test public void errorInvokesAllNonCanceledRequestsAndListener() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockCanceledRequest();
    List<Request> list = Arrays.asList(request1, request2);
    picasso.error(list, URI_1, null);
    verify(request1).error();
    verify(request2, never()).error();
    verifyZeroInteractions(listener);
  }

  @Test public void errorInvokesGlobalListenerWithException() throws Exception {
    Exception exception = mock(Exception.class);
    picasso.error(Collections.<Request>emptyList(), URI_1, exception);
    verify(listener).onImageLoadFailed(picasso, URI_1, exception);
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
    picasso.submit(request);
    assertThat(picasso.targetToRequest).hasSize(1);
    picasso.cancelRequest(target);
    assertThat(request.cancelled).isTrue();
    assertThat(picasso.targetToRequest).isEmpty();
    verify(dispatcher).dispatchCancel(request);
  }

  @Test public void cancelExistingRequestWithTarget() throws Exception {
    Target target = mockTarget();
    Request request = mockRequest(URI_KEY_1, URI_1, target);
    picasso.submit(request);
    assertThat(picasso.targetToRequest).hasSize(1);
    picasso.cancelRequest(target);
    assertThat(request.cancelled).isTrue();
    assertThat(picasso.targetToRequest).isEmpty();
    verify(dispatcher).dispatchCancel(request);
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
