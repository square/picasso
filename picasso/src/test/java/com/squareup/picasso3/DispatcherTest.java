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
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.TestUtils.TestDelegatingService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver;
import static com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver.EXTRA_AIRPLANE_STATE;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso3.Picasso.LoadedFrom.NETWORK;
import static com.squareup.picasso3.Request.KEY_SEPARATOR;
import static com.squareup.picasso3.TestUtils.URI_1;
import static com.squareup.picasso3.TestUtils.URI_2;
import static com.squareup.picasso3.TestUtils.URI_KEY_1;
import static com.squareup.picasso3.TestUtils.URI_KEY_2;
import static com.squareup.picasso3.TestUtils.makeBitmap;
import static com.squareup.picasso3.TestUtils.mockAction;
import static com.squareup.picasso3.TestUtils.mockCallback;
import static com.squareup.picasso3.TestUtils.mockHunter;
import static com.squareup.picasso3.TestUtils.mockNetworkInfo;
import static com.squareup.picasso3.TestUtils.mockPicasso;
import static com.squareup.picasso3.TestUtils.mockTarget;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class DispatcherTest {
  @Mock Context context;
  @Mock ConnectivityManager connectivityManager;
  @Mock ExecutorService serviceMock;
  final PlatformLruCache cache = new PlatformLruCache(2048);
  final TestDelegatingService service = new TestDelegatingService(new PicassoExecutorService());
  private Dispatcher dispatcher;

  final Bitmap bitmap1 = makeBitmap();
  final Bitmap bitmap2 = makeBitmap();

  @Before public void setUp() {
    initMocks(this);
    dispatcher = createDispatcher(service);
  }

  @Test public void shutdownStopsService() {
    PicassoExecutorService service = new PicassoExecutorService();
    dispatcher = createDispatcher(service);
    dispatcher.shutdown();
    assertThat(service.isShutdown()).isEqualTo(true);
  }

  @Test public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    shadowOf(getMainLooper()).idle();
    verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test public void performSubmitWithNewRequestQueuesHunter() {
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_2, URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(2);
    assertThat(service.submissions).isEqualTo(2);
  }

  @Test public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action1);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(service.submissions).isEqualTo(1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performSubmitWithShutdownServiceIgnoresRequest() {
    service.shutdown();
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions).isEmpty();

    FetchAction fetchAction1 =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).tag(pausedTag).build(), null);
    FetchAction fetchAction2 =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).tag(pausedTag).build(), null);
    dispatcher.performSubmit(fetchAction1);
    dispatcher.performSubmit(fetchAction2);

    assertThat(dispatcher.pausedActions).hasSize(2);
  }

  @Test public void performCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions).isEmpty();
    Callback callback = mockCallback();

    FetchAction fetchAction1 =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).tag(pausedTag).build(), callback);
    dispatcher.performCancel(fetchAction1);
    fetchAction1.cancel();
    assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test public void performCancelDetachesRequestAndCleansUp() {
    BitmapTarget target = mockTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1 + KEY_SEPARATOR, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.performCancel(action);
    assertThat(hunter.getAction()).isNull();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performCancelMultipleRequestsDetachesOnly() {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1 + KEY_SEPARATOR, hunter);
    dispatcher.performCancel(action1);
    assertThat(hunter.getAction()).isNull();
    assertThat(hunter.getActions()).containsExactly(action2);
    assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test public void performCancelUnqueuesAndDetachesPausedRequest() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag");
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1 + KEY_SEPARATOR, hunter);
    dispatcher.pausedTags.add("tag");
    dispatcher.pausedActions.put(action.getTarget(), action);
    dispatcher.performCancel(action);
    assertThat(hunter.getAction()).isNull();
    assertThat(dispatcher.pausedTags).containsExactly("tag");
    assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test public void performCompleteSetsResultInCache() {
    Request data = new Request.Builder(URI_1).build();
    Action action = noopAction(data);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    assertThat(hunter.getResult()).isInstanceOf(RequestHandler.Result.Bitmap.class);
    RequestHandler.Result.Bitmap result = (RequestHandler.Result.Bitmap) hunter.getResult();
    assertThat(result.getBitmap()).isEqualTo(bitmap1);
    assertThat(result.loadedFrom).isEqualTo(NETWORK);
    assertThat(cache.get(hunter.getKey())).isSameInstanceAs(bitmap1);
  }

  @Test public void performCompleteWithNoStoreMemoryPolicy() {
    Request data = new Request.Builder(URI_1).memoryPolicy(MemoryPolicy.NO_STORE).build();
    Action action = noopAction(data);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test public void performCompleteCleansUpAndPostsToMain() {
    Request data = new Request.Builder(URI_1).build();
    Action action = noopAction(data);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();

    dispatcher.performComplete(hunter);

    assertThat(dispatcher.hunterMap).isEmpty();
    // TODO verify post to main thread.
  }

  @Test public void performCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    Request data = new Request.Builder(URI_1).build();
    Action action = noopAction(data);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    hunter.future = new FutureTask<>(mock(Runnable.class), null);
    hunter.future.cancel(false);

    dispatcher.performComplete(hunter);

    assertThat(dispatcher.hunterMap).isEmpty();
    // TODO verify no main thread interactions.
  }

  @Test public void performErrorCleansUpAndPostsToMain() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag");
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(hunter.getKey(), hunter);
    dispatcher.performError(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    // TODO verify post to main thread.
  }

  @Test public void performErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag");
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.hunterMap.put(hunter.getKey(), hunter);
    dispatcher.performError(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    // TODO verify no main thread interactions.
  }

  @Test public void performRetrySkipsIfHunterIsCancelled() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag");
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.performRetry(hunter);
    assertThat(hunter.isCancelled()).isTrue();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Action action = mockAction(URI_KEY_2, URI_2);
    Exception e = new ContentLengthException("304 error");
    BitmapHunter hunter =
        mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, e, true);
    hunter.run();
    dispatcher.performRetry(hunter);
    assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse();
  }

  @Test public void performRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        mockAction(URI_KEY_1, URI_1));
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        mockAction(URI_KEY_1, URI_1), false, true);
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter =
        mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, false, true);
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performRetryRetriesIfNoNetworkScanning() {
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        mockAction(URI_KEY_1, URI_1), true);
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter =
        mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, false, true);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performRetryRetriesIfShouldRetry() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter =
        mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, true);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performRetrySkipIfServiceShutdown() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter = mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    service.shutdown();
    dispatcher.performRetry(hunter);
    assertThat(service.submissions).isEqualTo(0);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performAirplaneModeChange() {
    assertThat(dispatcher.airplaneMode).isFalse();
    dispatcher.performAirplaneModeChange(true);
    assertThat(dispatcher.airplaneMode).isTrue();
    dispatcher.performAirplaneModeChange(false);
    assertThat(dispatcher.airplaneMode).isFalse();
  }

  @Test public void performNetworkStateChangeWithNullInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    dispatcher.performNetworkStateChange(null);
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performNetworkStateChangeWithDisconnectedInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedTags).containsExactly("tag");
    dispatcher.performResumeTag("tag");
    assertThat(dispatcher.pausedTags).isEmpty();
  }

  @Test public void performPauseTagIsIdempotent() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag");
    BitmapHunter hunter =
        mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    assertThat(dispatcher.pausedActions).isEmpty();
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
  }

  @Test public void performPauseTagQueuesNewRequestDoesNotSubmit() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(URI_KEY_1, URI_1, "tag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(URI_KEY_1, URI_1, "anothertag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(dispatcher.pausedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performPauseDetachesRequestAndCancelsHunter() {
    Action action = mockAction(URI_KEY_1, URI_1, "tag");
    BitmapHunter hunter =
        mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    assertThat(hunter.getAction()).isNull();
  }

  @Test public void performPauseOnlyDetachesPausedRequest() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag1");
    Action action2 = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag2");
    BitmapHunter hunter =
        mockHunter(new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag1");
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue();
    assertThat(hunter.getAction()).isNull();
    assertThat(hunter.getActions()).containsExactly(action2);
  }

  @Test public void performResumeTagIsIdempotent() {
    dispatcher.performResumeTag("tag");
    // TODO verify no main thread interactions.
  }

  @Test public void performNetworkStateChangeFlushesFailedHunters() {
    NetworkInfo info = mockNetworkInfo(true);
    Action failedAction1 = mockAction(URI_KEY_1, URI_1);
    Action failedAction2 = mockAction(URI_KEY_2, URI_2);
    dispatcher.failedActions.put(URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(URI_KEY_2, failedAction2);
    dispatcher.performNetworkStateChange(info);
    assertThat(service.submissions).isEqualTo(2);
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void nullIntentOnReceiveDoesNothing() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, null);
    verifyZeroInteractions(dispatcher);
  }

  @Test public void nullExtrasOnReceiveConnectivityAreOk() {
    ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
    NetworkInfo networkInfo = mockNetworkInfo();
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    when(context.getSystemService(CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(CONNECTIVITY_ACTION));
    verify(dispatcher).dispatchNetworkStateChange(networkInfo);
  }

  @Test public void nullExtrasOnReceiveAirplaneDoesNothing() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(ACTION_AIRPLANE_MODE_CHANGED));
    verifyZeroInteractions(dispatcher);
  }

  @Test public void correctExtrasOnReceiveAirplaneDispatches() {
    setAndVerifyAirplaneMode(false);
    setAndVerifyAirplaneMode(true);
  }

  private void setAndVerifyAirplaneMode(boolean airplaneOn) {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    final Intent intent = new Intent(ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra(EXTRA_AIRPLANE_STATE, airplaneOn);
    receiver.onReceive(context, intent);
    verify(dispatcher).dispatchAirplaneModeChange(airplaneOn);
  }

  private Dispatcher createDispatcher(boolean scansNetworkChanges) {
    return createDispatcher(service, scansNetworkChanges);
  }

  private Dispatcher createDispatcher(ExecutorService service) {
    return createDispatcher(service, true);
  }

  private Dispatcher createDispatcher(ExecutorService service, boolean scansNetworkChanges) {
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(scansNetworkChanges ? mock(NetworkInfo.class) : null);
    when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    when(context.checkCallingOrSelfPermission(anyString())).thenReturn(
        scansNetworkChanges ? PERMISSION_GRANTED : PERMISSION_DENIED);
    return new Dispatcher(context, service, new Handler(Looper.getMainLooper()), cache);
  }

  private static Action noopAction(Request data) {
    return new Action(mockPicasso(), data) {
      @Override public void complete(@NonNull RequestHandler.Result result) {
      }

      @Override public void error(@NonNull Exception e) {
      }

      @NonNull @Override public Object getTarget() {
        throw new AssertionError();
      }
    };
  }
}
