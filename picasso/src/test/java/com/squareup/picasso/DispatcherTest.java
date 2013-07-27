package com.squareup.picasso;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.BITMAP_2;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_2;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.URI_KEY_2;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockNetworkInfo;
import static com.squareup.picasso.TestUtils.mockRequest;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
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
  private Dispatcher dispatcher;

  @Before public void setUp() throws Exception {
    initMocks(this);
    dispatcher = new Dispatcher(context, service, mainThreadHandler, downloader, cache);
  }

  @Test public void shutdownStopsService() throws Exception {
    dispatcher.shutdown();
    verify(service).shutdown();
  }

  @Test public void performSubmitWithNewRequestQueuesHunter() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    dispatcher.performSubmit(request);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithTwoDifferentRequestsQueuesHunters() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1);
    Request request2 = mockRequest(URI_KEY_2, URI_2);
    dispatcher.performSubmit(request1);
    dispatcher.performSubmit(request2);
    assertThat(dispatcher.hunterMap).hasSize(2);
    verify(service, times(2)).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithExistingRequestAttachesToHunter() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1);
    Request request2 = mockRequest(URI_KEY_1, URI_1);
    dispatcher.performSubmit(request1);
    dispatcher.performSubmit(request2);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(service).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithShutdownServiceIgnoresRequest() throws Exception {
    when(service.isShutdown()).thenReturn(true);
    Request request = mockRequest(URI_KEY_1, URI_1);
    dispatcher.performSubmit(request);
    assertThat(dispatcher.hunterMap).isEmpty();
    verify(service, never()).submit(any(BitmapHunter.class));
  }

  @Test public void performSubmitWithShutdownAttachesRequest() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    when(service.isShutdown()).thenReturn(true);
    Request request = mockRequest(URI_KEY_1, URI_1);
    dispatcher.performSubmit(request);
    assertThat(dispatcher.hunterMap).hasSize(1);
    verify(hunter).attach(request);
    verify(service, never()).submit(any(BitmapHunter.class));
  }

  @Test public void performCancelDetachesRequestAndCleansMap() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    hunter.attach(request);
    when(hunter.cancel()).thenReturn(true);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performCancel(request);
    verify(hunter).detach(request);
    verify(hunter).cancel();
    assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test public void performCancelMultipleRequestsDetachesOnly() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1);
    Request request2 = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    hunter.attach(request1);
    hunter.attach(request2);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performCancel(request1);
    verify(hunter).detach(request1);
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

  @Test public void performRetryTwoTimesBeforeError() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performRetry(hunter);
    verify(service).submit(hunter);
    dispatcher.performRetry(hunter);
    verify(service, times(2)).submit(hunter);
    dispatcher.performRetry(hunter);
    verify(service, times(3)).isShutdown();
    verifyNoMoreInteractions(service);
  }

  @Test public void performRetrySkipsRetryIfCancelled() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.performRetry(hunter);
    verifyZeroInteractions(service);
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
  public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() throws Exception {
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(true);
    dispatcher.performNetworkStateChange(info);
    verifyZeroInteractions(service);
  }

  @Test
  public void performNetworkStateChangeWithConnectedInfoAndPicassoExecutorServiceAdjustsThreads()
      throws Exception {
    PicassoExecutorService service = mock(PicassoExecutorService.class);
    Dispatcher dispatcher = new Dispatcher(context, service, mainThreadHandler, downloader, cache);
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(true);
    dispatcher.performNetworkStateChange(info);
    verify(service).adjustThreadCount(info);
    verifyZeroInteractions(service);
  }
}
