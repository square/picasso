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
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import com.squareup.picasso.NetworkRequestHandler.ContentLengthException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso.Dispatcher.NetworkBroadcastReceiver;
import static com.squareup.picasso.Dispatcher.NetworkBroadcastReceiver.EXTRA_AIRPLANE_STATE;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_2;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.URI_KEY_2;
import static com.squareup.picasso.TestUtils.makeBitmap;
import static com.squareup.picasso.TestUtils.mockAction;
import static com.squareup.picasso.TestUtils.mockCallback;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockNetworkInfo;
import static com.squareup.picasso.TestUtils.mockPicasso;
import static com.squareup.picasso.TestUtils.mockTarget;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
public class DispatcherTest {

  @Mock Context context;
  @Mock ConnectivityManager connectivityManager;
  @Mock PicassoExecutorService service;
  @Mock ExecutorService serviceMock;
  @Mock Handler mainThreadHandler;
  @Mock Downloader downloader;
  @Mock Cache cache;
  @Mock Stats stats;
  private Dispatcher dispatcher;

  final Bitmap bitmap1 = makeBitmap();
  final Bitmap bitmap2 = makeBitmap();

  @Before public void setUp() {
    initMocks(this);
    dispatcher = createDispatcher();
  }

  @Test public void shutdownStopsService() {
    dispatcher.shutdown();
    verify(service).shutdown();
  }

  @Test public void shutdownStopsDownloader() {
    dispatcher.shutdown();
    verify(downloader).shutdown();
  }

  @Test public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test public void performSubmitWithNewRequestQueuesHunter() {
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_2, URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(2);
    verify(service, times(2)).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithShutdownServiceIgnoresRequest() {
    when(service.isShutdown()).thenReturn(true);
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).isEmpty();
    verify(service, never()).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithShutdownAttachesRequest() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    when(service.isShutdown()).thenReturn(true);
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(hunter).attach(action);
    verify(service, never()).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions).isEmpty();

    FetchAction fetchAction1 =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).build(), 0, 0, pausedTag,
            URI_KEY_1, null);
    FetchAction fetchAction2 =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).build(), 0, 0, pausedTag,
            URI_KEY_1, null);
    dispatcher.performSubmit(fetchAction1);
    dispatcher.performSubmit(fetchAction2);

    assertThat(dispatcher.pausedActions).hasSize(2);
  }

  @Test public void performSubmitWithFetchActionWithSuccessCompletionCallback() {
    String pausedTag = "pausedTag";
    Callback callback = mockCallback();

    FetchAction fetchAction =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).build(), 0, 0, pausedTag,
            URI_KEY_1, callback);
    dispatcher.performSubmit(fetchAction);
    fetchAction.complete(bitmap1, MEMORY);

    verify(callback).onSuccess();
  }

  @Test public void performSubmitWithFetchActionWithErrorCompletionCallback() {
    String pausedTag = "pausedTag";
    Callback callback = mockCallback();

    FetchAction fetchAction =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).build(), 0, 0, pausedTag,
            URI_KEY_1, callback);
    dispatcher.performSubmit(fetchAction, false);
    Exception e = new RuntimeException();
    fetchAction.error(e);

    verify(callback).onError(e);
  }

  @Test public void performCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions).isEmpty();
    Callback callback = mockCallback();

    FetchAction fetchAction1 =
        new FetchAction(mockPicasso(), new Request.Builder(URI_1).build(), 0, 0, pausedTag,
            URI_KEY_1, callback);
    dispatcher.performCancel(fetchAction1);
    fetchAction1.cancel();
    assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test public void performCancelDetachesRequestAndCleansUp() {
    Target target = mockTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    hunter.attach(action);
    when(hunter.cancel()).thenReturn(true);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.performCancel(action);
    verify(hunter).detach(action);
    verify(hunter).cancel();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performCancelMultipleRequestsDetachesOnly() {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    hunter.attach(action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performCancel(action1);
    verify(hunter).detach(action1);
    verify(hunter).cancel();
    assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test public void performCancelUnqueuesAndDetachesPausedRequest() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag");
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.pausedTags.add("tag");
    dispatcher.pausedActions.put(action.getTarget(), action);
    dispatcher.performCancel(action);
    assertThat(dispatcher.pausedTags).containsExactly("tag");
    assertThat(dispatcher.pausedActions).isEmpty();
    verify(hunter).detach(action);
  }

  @Test public void performCompleteSetsResultInCache() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, 0, null);
    dispatcher.performComplete(hunter);
    verify(cache).set(hunter.getKey(), hunter.getResult());
  }

  @Test public void performCompleteWithNoStoreMemoryPolicy() {
    int memoryPolicy = 0;
    memoryPolicy |= MemoryPolicy.NO_STORE.index;
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, memoryPolicy, null);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    verifyZeroInteractions(cache);
  }

  @Test public void performCompleteCleansUpAndAddsToBatch() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).hasSize(1);
  }

  @Test public void performCompleteCleansUpAndDoesNotAddToBatchIfCancelled() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).isEmpty();
  }

  @Test public void performErrorCleansUpAndAddsToBatch() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    dispatcher.hunterMap.put(hunter.getKey(), hunter);
    dispatcher.performError(hunter, false);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).hasSize(1);
  }

  @Test public void performErrorCleansUpAndDoesNotAddToBatchIfCancelled() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.hunterMap.put(hunter.getKey(), hunter);
    dispatcher.performError(hunter, false);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).isEmpty();
  }

  @Test public void performBatchCompleteFlushesHunters() {
    BitmapHunter hunter1 = mockHunter(URI_KEY_2, bitmap1, false);
    BitmapHunter hunter2 = mockHunter(URI_KEY_2, bitmap2, false);
    dispatcher.batch.add(hunter1);
    dispatcher.batch.add(hunter2);
    dispatcher.performBatchComplete();
    assertThat(dispatcher.batch).isEmpty();
  }

  @Test public void performRetrySkipsIfHunterIsCancelled() {
    BitmapHunter hunter = mockHunter(URI_KEY_2, bitmap1, false);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.performRetry(hunter);
    verifyZeroInteractions(service);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    BitmapHunter hunter = mockHunter(URI_KEY_2, bitmap1, false);
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(true);
    when(hunter.getException()).thenReturn(new ContentLengthException("304 error"));
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.networkPolicy)).isFalse();
  }

  @Test public void performRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, mockAction(URI_KEY_1, URI_1));
    when(hunter.supportsReplay()).thenReturn(false);
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(false);
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    verify(service, never()).submit(hunter);
  }

  @Test public void performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, mockAction(URI_KEY_1, URI_1));
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(false);
    when(hunter.supportsReplay()).thenReturn(true);
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    verify(service, never()).submit(hunter);
  }

  @Test public void performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, action);
    when(hunter.supportsReplay()).thenReturn(true);
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(false);
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    verify(service, never()).submit(hunter);
  }

  @Test public void performRetryRetriesIfNoNetworkScanning() {
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, mockAction(URI_KEY_1, URI_1));
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(true);
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    verify(service).submit(hunter);
  }

  @Test public void performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, action);
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(false);
    when(hunter.supportsReplay()).thenReturn(true);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    verify(service, never()).submit(hunter);
  }

  @Test public void performRetryRetriesIfShouldRetry() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, action);
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(true);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    verify(service).submit(hunter);
  }

  @Test public void performRetrySkipIfServiceShutdown() {
    when(service.isShutdown()).thenReturn(true);
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    dispatcher.performRetry(hunter);
    verify(service, never()).submit(hunter);
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

  @Test public void performNetworkStateChangeWithNullInfo() {
    dispatcher.performNetworkStateChange(null);
    verify(service, times(1)).adjustThreadCount(null);
  }

  @Test public void performNetworkStateChangeWithDisconnectedInfo() {
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    verify(service, times(1)).adjustThreadCount(info);
  }

  @Test public void performNetworkStateChangeWithConnectedInfoDifferentInstance() {
    NetworkInfo info = mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    verify(service, times(1)).adjustThreadCount(info);
  }

  @Test public void performNetworkStateChangeWithNullInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    dispatcher.performNetworkStateChange(null);
    verifyZeroInteractions(service);
  }

  @Test public void performNetworkStateChangeWithDisconnectedInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    verifyZeroInteractions(service);
  }

  @Test public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    verifyZeroInteractions(service);
  }

  @Test public void performPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedTags).containsExactly("tag");
    dispatcher.performResumeTag("tag");
    assertThat(dispatcher.pausedTags).isEmpty();
  }

  @Test public void performPauseTagIsIdempotent() {
    Action action = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag");
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.pausedTags.add("tag");
    dispatcher.performPauseTag("tag");
    verify(hunter, never()).getAction();
  }

  @Test public void performPauseTagQueuesNewRequestDoesNotSubmit() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(URI_KEY_1, URI_1, "tag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    verify(service, never()).submit(any(BitmapHunter.class));
  }

  @Test public void performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(URI_KEY_1, URI_1, "anothertag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(dispatcher.pausedActions).isEmpty();
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performPauseDetachesRequestAndCancelsHunter() {
    Action action = mockAction(URI_KEY_1, URI_1, "tag");
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false, action);
    when(hunter.cancel()).thenReturn(true);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    verify(hunter).detach(action);
    verify(hunter).cancel();
  }

  @Test public void performPauseOnlyDetachesPausedRequest() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag1");
    Action action2 = mockAction(URI_KEY_1, URI_1, mockTarget(), "tag2");
    BitmapHunter hunter = mockHunter(URI_KEY_1, bitmap1, false);
    when(hunter.getActions()).thenReturn(Arrays.asList(action1, action2));
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag1");
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue();
    verify(hunter).detach(action1);
    verify(hunter, never()).detach(action2);
  }

  @Test public void performResumeTagIsIdempotent() {
    dispatcher.performResumeTag("tag");
    verify(mainThreadHandler, never()).sendMessage(any(Message.class));
  }

  @Test
  public void performNetworkStateChangeWithConnectedInfoAndPicassoExecutorServiceAdjustsThreads() {
    PicassoExecutorService service = mock(PicassoExecutorService.class);
    NetworkInfo info = mockNetworkInfo(true);
    Dispatcher dispatcher = createDispatcher(service);
    dispatcher.performNetworkStateChange(info);
    verify(service).adjustThreadCount(info);
    verifyZeroInteractions(service);
  }

  @Test public void performNetworkStateChangeFlushesFailedHunters() {
    PicassoExecutorService service = mock(PicassoExecutorService.class);
    NetworkInfo info = mockNetworkInfo(true);
    Dispatcher dispatcher = createDispatcher(service);
    Action failedAction1 = mockAction(URI_KEY_1, URI_1);
    Action failedAction2 = mockAction(URI_KEY_2, URI_2);
    dispatcher.failedActions.put(URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(URI_KEY_2, failedAction2);
    dispatcher.performNetworkStateChange(info);
    verify(service, times(2)).submit(any(BitmapHunter.class));
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

  private Dispatcher createDispatcher() {
    return createDispatcher(service);
  }

  private Dispatcher createDispatcher(boolean scansNetworkChanges) {
    return createDispatcher(service, scansNetworkChanges);
  }

  private Dispatcher createDispatcher(ExecutorService service) {
    return createDispatcher(service, true);
  }

  private Dispatcher createDispatcher(ExecutorService service, boolean scansNetworkChanges) {
    when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    when(context.checkCallingOrSelfPermission(anyString())).thenReturn(
        scansNetworkChanges ? PERMISSION_GRANTED : PERMISSION_DENIED);
    return new Dispatcher(context, service, mainThreadHandler, downloader, cache, stats);
  }
}
