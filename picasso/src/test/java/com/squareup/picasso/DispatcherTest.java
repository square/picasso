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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static com.squareup.picasso.Dispatcher.NetworkBroadcastReceiver;
import static com.squareup.picasso.Dispatcher.NetworkBroadcastReceiver.EXTRA_AIRPLANE_STATE;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.BITMAP_2;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_2;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.URI_KEY_2;
import static com.squareup.picasso.TestUtils.mockAction;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockNetworkInfo;
import static com.squareup.picasso.TestUtils.mockTarget;
import static java.util.Arrays.asList;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DispatcherTest {

  @Mock Context context;
  @Mock ExecutorService service;
  @Mock Handler mainThreadHandler;
  @Mock Downloader downloader;
  @Mock Cache cache;
  @Mock Stats stats;
  private Dispatcher dispatcher;

  @Before public void setUp() throws Exception {
    initMocks(this);
    dispatcher = new Dispatcher(context, service, mainThreadHandler, downloader, cache, stats);
  }

  @Test public void shutdownStopsService() throws Exception {
    dispatcher.shutdown();
    verify(service).shutdown();
  }

  @Test public void shutdownUnregistersReceiver() throws Exception {
    dispatcher.shutdown();
    verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test public void performSubmitWithNewRequestQueuesHunter() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithTwoDifferentRequestsQueuesHunters() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_2, URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(2);
    verify(service, times(2)).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithExistingRequestAttachesToHunter() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithShutdownServiceIgnoresRequest() throws Exception {
    when(service.isShutdown()).thenReturn(true);
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).isEmpty();
    verify(service, never()).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithShutdownAttachesRequest() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    when(service.isShutdown()).thenReturn(true);
    Action action = mockAction(URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(hunter).attach(action);
    verify(service, never()).submit(any(BitmapHunter.class));
  }

  @Test public void performCancelDetachesRequestAndCleansUp() throws Exception {
    Target target = mockTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
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

  @Test public void performCancelMultipleRequestsDetachesOnly() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1);
    Action action2 = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    hunter.attach(action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performCancel(action1);
    verify(hunter).detach(action1);
    verify(hunter).cancel();
    assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test public void performCompleteSetsResultInCache() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performComplete(hunter);
    verify(cache).set(hunter.getKey(), hunter.getResult());
  }

  @Test public void performCompleteWithSkipCacheDoesNotCache() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, true);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    verifyZeroInteractions(cache);
  }

  @Test public void performCompleteCleansUpAndAddsToBatch() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).hasSize(1);
  }

  @Test public void performCompleteCleansUpAndDoesNotAddToBatchIfCancelled() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).isEmpty();
  }

  @Test public void performErrorCleansUpAndAddsToBatch() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.hunterMap.put(hunter.getKey(), hunter);
    dispatcher.performError(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).hasSize(1);
  }

  @Test public void performErrorCleansUpAndDoesNotAddToBatchIfCancelled() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.hunterMap.put(hunter.getKey(), hunter);
    dispatcher.performError(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.batch).isEmpty();
  }

  @Test public void performBatchCompleteFlushesHunters() throws Exception {
    BitmapHunter hunter1 = mockHunter(URI_KEY_2, BITMAP_1, false);
    BitmapHunter hunter2 = mockHunter(URI_KEY_2, BITMAP_2, false);
    dispatcher.batch.add(hunter1);
    dispatcher.batch.add(hunter2);
    dispatcher.performBatchComplete();
    assertThat(dispatcher.batch).isEmpty();
  }

  @Test public void performRetrySkipsRetryIfCancelled() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.performRetry(hunter);
    verifyZeroInteractions(service);
  }

  @Test public void performRetrySkipsIfHunterSaysNo() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(false);
    dispatcher.performRetry(hunter);
    verify(service).isShutdown();
    verifyNoMoreInteractions(service);
  }

  @Test public void performRetryIfIsConnectedAndHunterSaysYes() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(true);
    dispatcher.performNetworkStateChange(mockNetworkInfo(true));
    dispatcher.performRetry(hunter);
    verify(service).submit(hunter);
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performRetryMarksForReplayIfReplaySupported() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false, mockAction(URI_KEY_1, URI_1));
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(false);
    when(hunter.supportsReplay()).thenReturn(true);
    dispatcher.performNetworkStateChange(mockNetworkInfo(false));
    dispatcher.performRetry(hunter);
    verify(service, never()).submit(hunter);
    assertThat(dispatcher.failedActions).hasSize(1);
  }

  @Test public void performRetryMarksForReplayIfShouldRetryAndReplaySupported() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false, mockAction(URI_KEY_1, URI_1));
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(true);
    when(hunter.supportsReplay()).thenReturn(true);
    dispatcher.performNetworkStateChange(mockNetworkInfo(false));
    dispatcher.performRetry(hunter);
    verify(service, never()).submit(hunter);
    assertThat(dispatcher.failedActions).hasSize(1);
  }

  @Test public void performRetryMarksForReplayAllActions() throws Exception {
    Action mockAction1 = mockAction(URI_KEY_1, URI_1, mockTarget());
    Action mockAction2 = mockAction(URI_KEY_1, URI_1, mockTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.getAction()).thenReturn(mockAction1);
    when(hunter.getActions()).thenReturn(asList(mockAction2));
    when(hunter.shouldRetry(anyBoolean(), any(NetworkInfo.class))).thenReturn(true);
    when(hunter.supportsReplay()).thenReturn(true);
    dispatcher.performNetworkStateChange(mockNetworkInfo(false));
    dispatcher.performRetry(hunter);
    verify(service, never()).submit(hunter);
    assertThat(dispatcher.failedActions).hasSize(2);
  }

  @Test public void performRetrySkipsRetryIfServiceShutdown() throws Exception {
    when(service.isShutdown()).thenReturn(true);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performRetry(hunter);
    verify(service, never()).submit(hunter);
  }

  @Test public void performAirplaneModeChange() throws Exception {
    assertThat(dispatcher.airplaneMode).isFalse();
    dispatcher.performAirplaneModeChange(true);
    assertThat(dispatcher.airplaneMode).isTrue();
    dispatcher.performAirplaneModeChange(false);
    assertThat(dispatcher.airplaneMode).isFalse();
  }

  @Test public void performNetworkStateChangeWithNullInfoIgnores() throws Exception {
    dispatcher.performNetworkStateChange(null);
    verifyZeroInteractions(service);
  }

  @Test public void performNetworkStateChangeWithDisconnectedInfoIgnores() throws Exception {
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    verifyZeroInteractions(service);
  }

  @Test
  public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores()
      throws Exception {
    NetworkInfo info = mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    verifyZeroInteractions(service);
  }

  @Test
  public void performNetworkStateChangeWithConnectedInfoAndPicassoExecutorServiceAdjustsThreads()
      throws Exception {
    PicassoExecutorService service = mock(PicassoExecutorService.class);
    NetworkInfo info = mockNetworkInfo(true);
    Dispatcher dispatcher =
        new Dispatcher(context, service, mainThreadHandler, downloader, cache, stats);
    dispatcher.performNetworkStateChange(info);
    verify(service).adjustThreadCount(info);
    verifyZeroInteractions(service);
  }

  @Test public void performNetworkStateChangeFlushesFailedHunters() throws Exception {
    PicassoExecutorService service = mock(PicassoExecutorService.class);
    NetworkInfo info = mockNetworkInfo(true);
    Dispatcher dispatcher =
        new Dispatcher(context, service, mainThreadHandler, downloader, cache, stats);
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
}
