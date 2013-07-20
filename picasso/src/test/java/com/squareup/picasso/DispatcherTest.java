package com.squareup.picasso;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_2;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.URI_KEY_2;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockNetworkInfo;
import static com.squareup.picasso.TestUtils.mockRequest;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
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

  @Test public void performCancelClearsOutFromFailedRequests()
      throws Exception {
    ImageView target = mockImageViewTarget();
    Request request = mockRequest(URI_KEY_1, URI_1, target);
    dispatcher.failedRequests.put(target, request);
    dispatcher.performCancel(request);
    assertThat(dispatcher.failedRequests).isEmpty();
  }

  @Test public void performCompleteSetsResultInCache() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    verify(cache).set(hunter.getKey(), hunter.getResult());
    verify(mainThreadHandler).sendMessage(any(Message.class));
  }

  @Test public void performCompleteWithSkipCacheDoesNotCache() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, true);
    dispatcher.performComplete(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    verifyZeroInteractions(cache);
    verify(mainThreadHandler).sendMessage(any(Message.class));
  }

  @Test public void performErrorCleansUp() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performSubmit(request);
    assertThat(dispatcher.hunterMap).hasSize(1);
    dispatcher.performError(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test public void performRetryTwoTimesBeforeError() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performRetry(hunter);
    verify(service).submit(hunter);
    dispatcher.performRetry(hunter);
    verify(service, times(2)).submit(hunter);
    dispatcher.performRetry(hunter);
    verifyNoMoreInteractions(service);
    assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test public void performRetrySkipsRetryIfCancelled() throws Exception {
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    dispatcher.performRetry(hunter);
    verify(service).submit(hunter);
    when(hunter.isCancelled()).thenReturn(true);
    dispatcher.performRetry(hunter);
    verifyNoMoreInteractions(service);
    assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test public void performRetryAddsToFailedRequests() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false, Arrays.asList(request), 0);
    assertThat(dispatcher.failedRequests).isEmpty();
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.failedRequests).hasSize(1);
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

  @Test public void performNetworkStateChangeWithConnectedInfoFlushesFailedRequests()
      throws Exception {
    ImageView target = mockImageViewTarget();
    Request request = mockRequest(URI_KEY_1, URI_1, target);
    dispatcher.failedRequests.put(target, request);
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(true);
    dispatcher.performNetworkStateChange(info);
    assertThat(dispatcher.failedRequests).isEmpty();
  }
}
