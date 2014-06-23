package com.squareup.picasso;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockAction;
import static com.squareup.picasso.TestUtils.mockHunter;
import static com.squareup.picasso.TestUtils.mockTarget;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Hannes Dorfmann
 */
@RunWith(RobolectricTestRunner.class) @Config(manifest = Config.NONE)
public class DispatchingQueueTest {

  @Mock Context context;
  @Mock ConnectivityManager connectivityManager;
  @Mock ExecutorService service;
  @Mock Handler mainThreadHandler;
  @Mock Downloader downloader;
  @Mock Cache cache;
  @Mock Stats stats;
  private Dispatcher dispatcher;
  private DispatchingQueue dispatchingQueue;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    dispatcher = createDispatcher();
    dispatchingQueue = dispatcher.dispatchingQueue;
  }

  @Test public void shutdownAndClearing() throws Exception {
    dispatcher.interruptDispatching();

    for (int i = 1; i <= 10; i++) {
      BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
      if (i % 2 == 0) {
        dispatcher.dispatchComplete(hunter);
      } else {
        dispatcher.dispatchFailed(hunter);
      }
      assertThat(dispatchingQueue.hunterMap).hasSize(i);
      assertThat(dispatchingQueue.jobQueue).hasSize(i);
    }

    dispatcher.shutdown();

    assertThat(dispatchingQueue.hunterMap).isEmpty();
    assertThat(dispatchingQueue.jobQueue).isEmpty();
  }

  @Test
  public void interruptingContinuing() {

    // At the beginning dispatching should be enabled
    assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();

    dispatcher.interruptDispatching();
    assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();


    dispatcher.continueDispatching();
    assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();

    dispatcher.interruptDispatching();
    assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();

    dispatcher.continueDispatching();
    assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();

  }

  private DispatchingQueue.DispatchJob getJobFromQueue(BitmapHunter hunter) {
    for (DispatchingQueue.DispatchJob job : dispatchingQueue.jobQueue) {
      if (job.getBitmapHunter() == hunter) {
        return job;
      }
    }

    return null;
  }

  @Test
  public void addingRemovingToQueue() {

    int tests = new Random().nextInt(10) + 5;

    for (int j = 0; j < tests; j++) {

      // Interrupt dispatching
      dispatcher.interruptDispatching();
      assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();

      for (int i = 1; i <= 10; i++) {
        BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
        if (i % 2 == 0) {
          dispatcher.dispatchComplete(hunter);
        } else {
          dispatcher.dispatchFailed(hunter);
        }
        assertThat(dispatchingQueue.hunterMap).hasSize(i);
        assertThat(dispatchingQueue.jobQueue).hasSize(i);

        assertThat(dispatchingQueue.hunterMap).containsKey(hunter);
        assertThat(dispatchingQueue.hunterMap.get(hunter)).isNotNull();

        DispatchingQueue.DispatchJob job = getJobFromQueue(hunter);
        assertThat(job).isNotNull();
        assertThat(dispatchingQueue.hunterMap.get(hunter)).isSameAs(job);
      }

      // Continue Dispatching
      dispatcher.continueDispatching();
      assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();
      assertThat(dispatchingQueue.hunterMap).isEmpty();
      assertThat(dispatchingQueue.jobQueue).isEmpty();
    }
  }

  @Test
  public void performCancel() {

    Target target = mockTarget();
    Action action = mockAction(URI_KEY_1, URI_1, target);
    BitmapHunter hunter = mockHunter(URI_KEY_1, BITMAP_1, false);
    hunter.attach(action);
    when(hunter.cancel()).thenReturn(true);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.interruptDispatching();
    assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();
    dispatchingQueue.dispatchComplete(hunter);
    assertThat(dispatchingQueue.hunterMap).containsKey(hunter);
    assertThat(getJobFromQueue(hunter)).isNotNull();
    dispatcher.performCancel(action);
    verify(hunter).detach(action);
    verify(hunter).cancel();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(dispatchingQueue.hunterMap).isEmpty();
    assertThat(dispatchingQueue.jobQueue).isEmpty();
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
