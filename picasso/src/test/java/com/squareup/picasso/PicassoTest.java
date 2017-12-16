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
import android.net.Uri;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso.Picasso.Listener;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.RemoteViewsAction.RemoteViewsTarget;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.makeBitmap;
import static com.squareup.picasso.TestUtils.mockAction;
import static com.squareup.picasso.TestUtils.mockCanceledAction;
import static com.squareup.picasso.TestUtils.mockDeferredRequestCreator;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockTarget;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
@Config(sdk = 23) // Works around https://github.com/robolectric/robolectric/issues/2566.
public class PicassoTest {

  @Mock Context context;
  @Mock Downloader downloader;
  @Mock Dispatcher dispatcher;
  @Mock Picasso.RequestTransformer transformer;
  @Mock RequestHandler requestHandler;
  @Mock Cache cache;
  @Mock Listener listener;
  @Mock Stats stats;

  private Picasso picasso;
  final Bitmap bitmap = makeBitmap();

  @Before public void setUp() {
    initMocks(this);
    picasso = new Picasso(context, dispatcher, cache, listener, transformer, null, stats, ARGB_8888,
        false, false);
  }

  @Test public void submitWithNullTargetInvokesDispatcher() {
    Action action = mockAction(URI_KEY_1, URI_1);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).isEmpty();
    verify(dispatcher).dispatchSubmit(action);
  }

  @Test public void submitWithTargetInvokesDispatcher() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    assertThat(picasso.targetToAction).isEmpty();
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    verify(dispatcher).dispatchSubmit(action);
  }

  @Test public void submitWithSameActionDoesNotCancel() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    picasso.enqueueAndSubmit(action);
    verify(dispatcher).dispatchSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(picasso.targetToAction.containsValue(action)).isTrue();
    picasso.enqueueAndSubmit(action);
    verify(action, never()).cancel();
    verify(dispatcher, never()).dispatchCancel(action);
  }

  @Test public void quickMemoryCheckReturnsBitmapIfInCache() {
    when(cache.get(URI_KEY_1)).thenReturn(bitmap);
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    assertThat(cached).isEqualTo(bitmap);
    verify(stats).dispatchCacheHit();
  }

  @Test public void quickMemoryCheckReturnsNullIfNotInCache() {
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    assertThat(cached).isNull();
    verify(stats).dispatchCacheMiss();
  }

  @Test public void completeInvokesSuccessOnAllSuccessfulRequests() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockCanceledAction();
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap, false);
    when(hunter.getActions()).thenReturn(Arrays.asList(action1, action2));
    when(hunter.getLoadedFrom()).thenReturn(MEMORY);
    picasso.complete(hunter);
    verify(action1).complete(bitmap, MEMORY);
    verify(action2, never()).complete(eq(bitmap), any(Picasso.LoadedFrom.class));
  }

  @Test public void completeInvokesErrorOnAllFailedRequests() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockCanceledAction();
    Exception exception = mock(Exception.class);
    BitmapHunter hunter = mockHunter(URI_KEY_1, null, false);
    when(hunter.getException()).thenReturn(exception);
    when(hunter.getActions()).thenReturn(Arrays.asList(action1, action2));
    picasso.complete(hunter);
    verify(action1).error(exception);
    verify(action2, never()).error(exception);
    verify(listener).onImageLoadFailed(picasso, URI_1, exception);
  }

  @Test public void completeDeliversToSingle() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap, false);
    when(hunter.getLoadedFrom()).thenReturn(MEMORY);
    when(hunter.getAction()).thenReturn(action);
    when(hunter.getActions()).thenReturn(Collections.<Action>emptyList());
    picasso.complete(hunter);
    verify(action).complete(bitmap, MEMORY);
  }

  @Test public void completeWithReplayDoesNotRemove() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    when(action.willReplay()).thenReturn(true);
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap, false);
    when(hunter.getLoadedFrom()).thenReturn(MEMORY);
    when(hunter.getAction()).thenReturn(action);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    picasso.complete(hunter);
    assertThat(picasso.targetToAction).hasSize(1);
    verify(action).complete(bitmap, MEMORY);
  }

  @Test public void completeDeliversToSingleAndMultiple() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap, false);
    when(hunter.getLoadedFrom()).thenReturn(MEMORY);
    when(hunter.getAction()).thenReturn(action);
    when(hunter.getActions()).thenReturn(Arrays.asList(action2));
    picasso.complete(hunter);
    verify(action).complete(bitmap, MEMORY);
    verify(action2).complete(bitmap, MEMORY);
  }

  @Test public void completeSkipsIfNoActions() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap, false);
    picasso.complete(hunter);
    verify(hunter).getAction();
    verify(hunter).getActions();
    verifyNoMoreInteractions(hunter);
  }

  @Test public void loadedFromIsNullThrows() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap, false);
    when(hunter.getAction()).thenReturn(action);
    try {
      picasso.complete(hunter);
      fail("Calling complete() with null LoadedFrom should throw");
    } catch (AssertionError expected) {
    }
  }

  @Test public void resumeActionTriggersSubmitOnPausedAction() {
    Action action = mockAction(URI_KEY_1, URI_1);
    picasso.resumeAction(action);
    verify(dispatcher).dispatchSubmit(action);
  }

  @Test public void resumeActionImmediatelyCompletesCachedRequest() {
    when(cache.get(URI_KEY_1)).thenReturn(bitmap);
    Action action = mockAction(URI_KEY_1, URI_1);
    picasso.resumeAction(action);
    verify(action).complete(bitmap, MEMORY);
  }

  @Test public void cancelExistingRequestWithUnknownTarget() {
    ImageView target = mockImageViewTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    picasso.cancelRequest(target);
    verifyZeroInteractions(action, dispatcher);
  }

  @Test public void cancelExistingRequestWithNullImageView() {
    try {
      picasso.cancelRequest((ImageView) null);
      fail("Canceling with a null ImageView should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void cancelExistingRequestWithNullTarget() {
    try {
      picasso.cancelRequest((Target) null);
      fail("Canceling with a null target should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void cancelExistingRequestWithImageViewTarget() {
    ImageView target = mockImageViewTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    verify(action).cancel();
    verify(dispatcher).dispatchCancel(action);
  }

  @Test public void cancelExistingRequestWithDeferredImageViewTarget() {
    ImageView target = mockImageViewTarget();
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator();
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    verify(deferredRequestCreator).cancel();
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test public void enqueueingDeferredRequestCancelsThePreviousOne() throws Exception {
    ImageView target = mockImageViewTarget();
    DeferredRequestCreator firstRequestCreator = mockDeferredRequestCreator();
    picasso.defer(target, firstRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);

    DeferredRequestCreator secondRequestCreator = mockDeferredRequestCreator();
    picasso.defer(target, secondRequestCreator);
    verify(firstRequestCreator).cancel();
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);
  }

  @Test public void cancelExistingRequestWithTarget() {
    Target target = mockTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    verify(action).cancel();
    verify(dispatcher).dispatchCancel(action);
  }

  @Test public void cancelExistingRequestWithNullRemoteViews() {
    try {
      picasso.cancelRequest(null, 0);
      fail("Canceling with a null RemoteViews should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Config(sdk = 16) // This test fails on 23 so restore the default level.
  @Test public void cancelExistingRequestWithRemoteViewTarget() {
    int layoutId = 0;
    int viewId = 1;
    RemoteViews remoteViews = new RemoteViews("packageName", layoutId);
    RemoteViewsTarget target = new RemoteViewsTarget(remoteViews, viewId);
    Action action = mockAction(URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    picasso.cancelRequest(remoteViews, viewId);
    assertThat(picasso.targetToAction).isEmpty();
    verify(action).cancel();
    verify(dispatcher).dispatchCancel(action);
  }

  @Test public void cancelNullTagThrows() {
    try {
      picasso.cancelTag(null);
      fail("Canceling with a null tag should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void cancelTagAllActions() {
    ImageView target = mockImageViewTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target, "TAG");
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    picasso.cancelTag("TAG");
    assertThat(picasso.targetToAction).isEmpty();
    verify(action).cancel();
  }

  @Test public void cancelTagAllDeferredRequests() {
    ImageView target = mockImageViewTarget();
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator();
    when(deferredRequestCreator.getTag()).thenReturn("TAG");
    picasso.defer(target, deferredRequestCreator);
    picasso.cancelTag("TAG");
    verify(deferredRequestCreator).cancel();
  }

  @Test public void deferAddsToMap() {
    ImageView target = mockImageViewTarget();
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator();
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
    picasso.defer(target, deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
  }

  @Test public void shutdown() {
    picasso.shutdown();
    verify(cache).clear();
    verify(stats).shutdown();
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
  }

  @Test public void shutdownTwice() {
    picasso.shutdown();
    picasso.shutdown();
    verify(cache).clear();
    verify(stats).shutdown();
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
  }

  @Test public void shutdownDisallowedOnSingletonInstance() {
    Picasso.singleton = null;
    PicassoProvider.context = RuntimeEnvironment.application;
    try {
      Picasso.get().shutdown();
      fail("Calling shutdown() on static singleton instance should throw");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test public void shutdownDisallowedOnCustomSingletonInstance() {
    Picasso.singleton = null;
    try {
      Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).build();
      Picasso.setSingletonInstance(picasso);
      picasso.shutdown();
      fail("Calling shutdown() on static singleton instance should throw");
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test public void setSingletonInstanceRejectsNull() {
    Picasso.singleton = null;

    try {
      Picasso.setSingletonInstance(null);
      fail("Can't set singleton instance to null.");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Picasso must not be null.");
    }
  }

  @Test public void setSingletonInstanceMayOnlyBeCalledOnce() {
    Picasso.singleton = null;

    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).build();
    Picasso.setSingletonInstance(picasso);

    try {
      Picasso.setSingletonInstance(picasso);
      fail("Can't set singleton instance twice.");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Singleton instance already exists.");
    }
  }

  @Test public void setSingletonInstanceAfterWithFails() {
    Picasso.singleton = null;
    PicassoProvider.context = RuntimeEnvironment.application;

    // Implicitly create the default singleton instance.
    Picasso.get();

    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).build();
    try {
      Picasso.setSingletonInstance(picasso);
      fail("Can't set singleton instance after with().");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Singleton instance already exists.");
    }
  }

  @Test public void setSingleInstanceReturnedFromWith() {
    Picasso.singleton = null;
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).build();
    Picasso.setSingletonInstance(picasso);
    assertThat(Picasso.get()).isSameAs(picasso);
  }

  @Test public void shutdownClearsDeferredRequests() {
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator();
    ImageView target = mockImageViewTarget();
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.shutdown();
    verify(deferredRequestCreator).cancel();
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test public void whenTransformRequestReturnsNullThrows() {
    try {
      when(transformer.transformRequest(any(Request.class))).thenReturn(null);
      picasso.transformRequest(new Request.Builder(URI_1).build());
      fail("Returning null from transformRequest() should throw");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void getSnapshotInvokesStats() {
    picasso.getSnapshot();
    verify(stats).createSnapshot();
  }

  @Test public void enableIndicators() {
    assertThat(picasso.areIndicatorsEnabled()).isFalse();
    picasso.setIndicatorsEnabled(true);
    assertThat(picasso.areIndicatorsEnabled()).isTrue();
  }

  @Test public void loadThrowsWithInvalidInput() {
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

  @Test public void builderInvalidListener() {
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

  @Test public void builderInvalidLoader() {
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

  @Test public void builderInvalidExecutor() {
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

  @Test public void builderInvalidCache() {
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

  @Test public void builderInvalidRequestTransformer() {
    try {
      new Picasso.Builder(context).requestTransformer(null);
      fail("Null request transformer should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).requestTransformer(transformer).requestTransformer(transformer);
      fail("Setting request transformer twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidRequestHandler() {
    try {
      new Picasso.Builder(context).addRequestHandler(null);
      fail("Null request handler should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder(context).addRequestHandler(requestHandler)
          .addRequestHandler(requestHandler);
      fail("Registering same request handler twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderWithoutRequestHandler() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).build();
    assertThat(picasso.getRequestHandlers()).isNotEmpty();
    assertThat(picasso.getRequestHandlers()).doesNotContain(requestHandler);
  }

  @Test public void builderWithRequestHandler() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application)
        .addRequestHandler(requestHandler).build();
    assertThat(picasso.getRequestHandlers()).isNotNull();
    assertThat(picasso.getRequestHandlers()).isNotEmpty();
    assertThat(picasso.getRequestHandlers()).contains(requestHandler);
  }

  @Test public void builderInvalidContext() {
    try {
      new Picasso.Builder(null);
      fail("Null context should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void builderWithDebugIndicators() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    assertThat(picasso.areIndicatorsEnabled()).isTrue();
  }

  @Test public void invalidateString() {
    picasso.invalidate("http://example.com");
    verify(cache).clearKeyUri("http://example.com");
  }

  @Test public void invalidateFile() {
    picasso.invalidate(new File("/foo/bar/baz"));
    verify(cache).clearKeyUri("file:///foo/bar/baz");
  }

  @Test public void invalidateUri() {
    picasso.invalidate(Uri.parse("mock://12345"));
    verify(cache).clearKeyUri("mock://12345");
  }
}
