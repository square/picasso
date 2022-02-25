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
package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import com.squareup.picasso3.Picasso.RequestTransformer;
import com.squareup.picasso3.TestUtils.EventRecorder;
import com.squareup.picasso3.TestUtils.FakeAction;
import java.io.File;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static android.graphics.Bitmap.Config.ALPHA_8;
import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso3.Picasso.Listener;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso3.Picasso.LoadedFrom.NETWORK;
import static com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget;
import static com.squareup.picasso3.TestUtils.NOOP_REQUEST_HANDLER;
import static com.squareup.picasso3.TestUtils.NOOP_TRANSFORMER;
import static com.squareup.picasso3.TestUtils.NO_EVENT_LISTENERS;
import static com.squareup.picasso3.TestUtils.NO_HANDLERS;
import static com.squareup.picasso3.TestUtils.NO_TRANSFORMERS;
import static com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY;
import static com.squareup.picasso3.TestUtils.URI_1;
import static com.squareup.picasso3.TestUtils.URI_KEY_1;
import static com.squareup.picasso3.TestUtils.defaultPicasso;
import static com.squareup.picasso3.TestUtils.makeBitmap;
import static com.squareup.picasso3.TestUtils.mockAction;
import static com.squareup.picasso3.TestUtils.mockDeferredRequestCreator;
import static com.squareup.picasso3.TestUtils.mockHunter;
import static com.squareup.picasso3.TestUtils.mockImageViewTarget;
import static com.squareup.picasso3.TestUtils.mockPicasso;
import static com.squareup.picasso3.TestUtils.mockRequestCreator;
import static com.squareup.picasso3.TestUtils.mockTarget;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23) // Works around https://github.com/robolectric/robolectric/issues/2566.
public final class PicassoTest {
  private static final int NUM_BUILTIN_HANDLERS = 8;
  private static final int NUM_BUILTIN_TRANSFORMERS = 0;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Mock Context context;
  @Mock Dispatcher dispatcher;
  @Mock RequestHandler requestHandler;
  final PlatformLruCache cache = new PlatformLruCache(2048);
  @Mock Listener listener;

  private Picasso picasso;
  private EventRecorder eventRecorder = new EventRecorder();
  final Bitmap bitmap = makeBitmap();

  @Before public void setUp() {
    initMocks(this);
    picasso = new Picasso(context, dispatcher, UNUSED_CALL_FACTORY, null, cache, listener,
        NO_TRANSFORMERS, NO_HANDLERS, Collections.singletonList(eventRecorder), ARGB_8888, false,
        false);
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
    assertThat(action.cancelled).isFalse();
    verify(dispatcher, never()).dispatchCancel(action);
  }

  @Test public void quickMemoryCheckReturnsBitmapIfInCache() {
    cache.set(URI_KEY_1, bitmap);
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    assertThat(cached).isEqualTo(bitmap);
    assertThat(eventRecorder.cacheHits).isGreaterThan(0);
  }

  @Test public void quickMemoryCheckReturnsNullIfNotInCache() {
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    assertThat(cached).isNull();
    assertThat(eventRecorder.cacheMisses).isGreaterThan(0);
  }

  @Test public void completeInvokesSuccessOnAllSuccessfulRequests() {
    FakeAction action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap, MEMORY), action1);
    FakeAction action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;

    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action1);
    assertThat(action2.completedResult).isNull();
  }

  @Test public void completeInvokesErrorOnAllFailedRequests() {
    FakeAction action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Exception exception = mock(Exception.class);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap, MEMORY), action1, exception);
    FakeAction action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;
    hunter.run();
    picasso.complete(hunter);

    assertThat(action1.errorException).hasCauseThat().isEqualTo(exception);
    assertThat(action2.errorException).isNull();
    verify(listener).onImageLoadFailed(picasso, URI_1, action1.errorException);
  }

  @Test public void completeDeliversToSingle() {
    FakeAction action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
  }

  @Test public void completeWithReplayDoesNotRemove() {
    FakeAction action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    action.willReplay = true;
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.run();
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    picasso.complete(hunter);
    assertThat(picasso.targetToAction).hasSize(1);

    verifyActionComplete(action);
  }

  @Test public void completeDeliversToSingleAndMultiple() {
    FakeAction action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    FakeAction action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
    verifyActionComplete(action2);
  }

  @Test public void completeSkipsIfNoActions() {
    FakeAction action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.detach(action);
    hunter.run();
    picasso.complete(hunter);

    assertThat(hunter.getAction()).isNull();
    assertThat(hunter.getActions()).isNull();
  }

  @Test public void resumeActionTriggersSubmitOnPausedAction() {
    Request request = new Request.Builder(URI_1, 0, ARGB_8888).build();
    Action action =
        new Action(mockPicasso(), request) {
          @Override public void complete(RequestHandler.Result result) {
            fail("Test execution should not call this method");
          }

          @Override public void error(Exception e) {
            fail("Test execution should not call this method");
          }

          @NonNull @Override public Object getTarget() {
            return this;
          }
        };
    picasso.resumeAction(action);
    verify(dispatcher).dispatchSubmit(action);
  }

  @Test public void resumeActionImmediatelyCompletesCachedRequest() {
    cache.set(URI_KEY_1, bitmap);
    Request request = new Request.Builder(URI_1, 0, ARGB_8888).build();
    Action action =
        new Action(mockPicasso(), request) {
          @Override public void complete(RequestHandler.Result result) {
            assertThat(result).isInstanceOf(RequestHandler.Result.Bitmap.class);
            RequestHandler.Result.Bitmap bitmapResult = (RequestHandler.Result.Bitmap) result;
            assertThat(bitmapResult.getBitmap()).isEqualTo(bitmap);
            assertThat(bitmapResult.loadedFrom).isEqualTo(MEMORY);
          }

          @Override public void error(Exception e) {
            fail("Reading from memory cache should not throw an exception");
          }

          @NonNull @Override public Object getTarget() {
            return this;
          }
        };

    picasso.resumeAction(action);
  }

  @Test public void cancelExistingRequestWithUnknownTarget() {
    ImageView target = mockImageViewTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(action.cancelled).isFalse();
    verifyZeroInteractions(dispatcher);
  }

  @Test public void cancelExistingRequestWithNullImageView() {
    try {
      picasso.cancelRequest((ImageView) null);
      fail("Canceling with a null ImageView should throw exception.");
    } catch (NullPointerException expected) {
    }
  }

  @Test public void cancelExistingRequestWithNullTarget() {
    try {
      picasso.cancelRequest((BitmapTarget) null);
      fail("Canceling with a null target should throw exception.");
    } catch (NullPointerException expected) {
    }
  }

  @Test public void cancelExistingRequestWithImageViewTarget() {
    ImageView target = mockImageViewTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    verify(dispatcher).dispatchCancel(action);
  }

  @Test public void cancelExistingRequestWithDeferredImageViewTarget() {
    ImageView target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator();
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test public void enqueueingDeferredRequestCancelsThePreviousOne() {
    ImageView target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator();
    DeferredRequestCreator firstRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, firstRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);

    DeferredRequestCreator secondRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, secondRequestCreator);
    verify(target).removeOnAttachStateChangeListener(firstRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);
  }

  @Test public void cancelExistingRequestWithTarget() {
    BitmapTarget target = mockTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    verify(dispatcher).dispatchCancel(action);
  }

  @Test public void cancelExistingRequestWithNullRemoteViews() {
    try {
      picasso.cancelRequest(null, 0);
      fail("Canceling with a null RemoteViews should throw exception.");
    } catch (NullPointerException expected) {
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
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(remoteViews, viewId);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    verify(dispatcher).dispatchCancel(action);
  }

  @Test public void cancelNullTagThrows() {
    try {
      picasso.cancelTag(null);
      fail("Canceling with a null tag should throw exception.");
    } catch (NullPointerException expected) {
    }
  }

  @Test public void cancelTagAllActions() {
    ImageView target = mockImageViewTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target, "TAG");
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelTag("TAG");
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
  }

  @Test public void cancelTagAllDeferredRequests() {
    ImageView target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator().tag("TAG");
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, deferredRequestCreator);
    picasso.cancelTag("TAG");
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
  }

  @Test public void deferAddsToMap() {
    ImageView target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator();
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
    picasso.defer(target, deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
  }

  @Test public void shutdown() {
    cache.set("key", makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(eventRecorder.closed).isTrue();
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
  }

  @Test public void shutdownClosesUnsharedCache() {
    okhttp3.Cache cache = new okhttp3.Cache(temporaryFolder.getRoot(), 100);
    Picasso picasso =
        new Picasso(context, dispatcher, UNUSED_CALL_FACTORY, cache, this.cache, listener,
            NO_TRANSFORMERS, NO_HANDLERS, Collections.singletonList(eventRecorder), ARGB_8888,
            false, false);
    picasso.shutdown();
    assertThat(cache.isClosed()).isTrue();
  }

  @Test public void shutdownTwice() {
    cache.set("key", makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    picasso.shutdown();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(eventRecorder.closed).isTrue();
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
  }

  @Test public void shutdownClearsDeferredRequests() {
    ImageView target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator();
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.shutdown();
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test public void throwWhenTransformRequestReturnsNull() {
    RequestTransformer brokenTransformer = request -> null;
    Picasso picasso = new Picasso(context, dispatcher, UNUSED_CALL_FACTORY, null, cache, listener,
        Collections.singletonList(brokenTransformer), NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
        false, false);
    Request request = new Request.Builder(URI_1).build();
    try {
      picasso.transformRequest(request);
      fail("Returning null from transformRequest() should throw");
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessageThat()
          .isEqualTo("Request transformer "
              + brokenTransformer.getClass().getCanonicalName()
              + " returned null for "
              + request);
    }
  }

  @Test public void enableIndicators() {
    assertThat(picasso.getIndicatorsEnabled()).isFalse();
    picasso.setIndicatorsEnabled(true);
    assertThat(picasso.getIndicatorsEnabled()).isTrue();
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

  @Test public void builderInvalidClient() {
    try {
      new Picasso.Builder(context).client(null);
      fail();
    } catch (NullPointerException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("client == null");
    }
    try {
      new Picasso.Builder(context).callFactory(null);
      fail();
    } catch (NullPointerException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("factory == null");
    }
  }

  @Test public void builderInvalidCache() {
    try {
      new Picasso.Builder(context).withCacheSize(-1);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("maxByteCount < 0: -1");
    }
  }

  @Test public void builderNullRequestTransformer() {
    try {
      new Picasso.Builder(context).addRequestTransformer(null);
      fail("Null request transformer should throw exception.");
    } catch (NullPointerException expected) {
    }
  }

  @Test public void builderNullRequestHandler() {
    try {
      new Picasso.Builder(context).addRequestHandler(null);
      fail("Null request handler should throw exception.");
    } catch (NullPointerException expected) {
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
      new Picasso.Builder((Context) null);
      fail("Null context should throw exception.");
    } catch (NullPointerException expected) {
    }
  }

  @Test public void builderWithDebugIndicators() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    assertThat(picasso.getIndicatorsEnabled()).isTrue();
  }

  @Test public void evictAll() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    picasso.cache.set("key", Bitmap.createBitmap(1, 1, ALPHA_8));
    assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.evictAll();
    assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test public void invalidateString() {
    Request request = new Request.Builder(Uri.parse("https://example.com")).build();
    cache.set(request.key, makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.invalidate("https://example.com");
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test public void invalidateFile() {
    Request request = new Request.Builder(Uri.fromFile(new File("/foo/bar/baz"))).build();
    cache.set(request.key, makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.invalidate(new File("/foo/bar/baz"));
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test public void invalidateUri() {
    Request request = new Request.Builder(URI_1).build();
    cache.set(request.key, makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.invalidate(URI_1);
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test public void clonedRequestHandlersAreIndependent() {
    Picasso original = defaultPicasso(RuntimeEnvironment.application, false, false);

    original.newBuilder()
        .addRequestTransformer(NOOP_TRANSFORMER)
        .addRequestHandler(NOOP_REQUEST_HANDLER)
        .build();

    assertThat(original.requestTransformers).hasSize(NUM_BUILTIN_TRANSFORMERS);
    assertThat(original.requestHandlers).hasSize(NUM_BUILTIN_HANDLERS);
  }

  @Test public void cloneSharesStatefulInstances() {
    Picasso parent = defaultPicasso(RuntimeEnvironment.application, true, true);

    Picasso child = parent.newBuilder()
        .build();

    assertThat(child.context).isEqualTo(parent.context);
    assertThat(child.callFactory).isEqualTo(parent.callFactory);
    assertThat(child.dispatcher.service).isEqualTo(parent.dispatcher.service);
    assertThat(child.cache).isEqualTo(parent.cache);
    assertThat(child.listener).isEqualTo(parent.listener);
    assertThat(child.requestTransformers).isEqualTo(parent.requestTransformers);

    assertThat(child.requestHandlers).hasSize(parent.requestHandlers.size());
    for (int i = 0, n = child.requestHandlers.size(); i < n; i++) {
      assertThat(child.requestHandlers.get(i)).isInstanceOf(
          parent.requestHandlers.get(i).getClass());
    }

    assertThat(child.defaultBitmapConfig).isEqualTo(parent.defaultBitmapConfig);
    assertThat(child.indicatorsEnabled).isEqualTo(parent.indicatorsEnabled);
    assertThat(child.loggingEnabled).isEqualTo(parent.loggingEnabled);

    assertThat(child.targetToAction).isEqualTo(parent.targetToAction);
    assertThat(child.targetToDeferredRequestCreator).isEqualTo(
        parent.targetToDeferredRequestCreator);
  }

  private void verifyActionComplete(FakeAction action) {
    RequestHandler.Result result = action.completedResult;
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(RequestHandler.Result.Bitmap.class);
    RequestHandler.Result.Bitmap bitmapResult = (RequestHandler.Result.Bitmap) result;
    assertThat(bitmapResult.getBitmap()).isEqualTo(bitmap);
    assertThat(bitmapResult.loadedFrom).isEqualTo(NETWORK);
  }
}
