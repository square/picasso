package com.squareup.picasso;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Random;
import java.util.concurrent.ExecutorService;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockHunter;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Hannes Dorfmann
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DispatchingQueueTest {

    @Mock
    Context context;
    @Mock
    ConnectivityManager connectivityManager;
    @Mock
    ExecutorService service;
    @Mock
    Handler mainThreadHandler;
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

        for (int i = 1 ; i<= 10; i++){
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
    public void interruptingContinuing(){

        // At the beginning dispatching should be enabled
        assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();

        dispatcher.interruptDispatching();
        assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();

        dispatcher.interruptDispatching();
        assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();


        dispatcher.continueDispatching();
        assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();

        dispatcher.continueDispatching();
        assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();

        // Do random testing
        Random r = new Random();
        int tests = r.nextInt(20)+10;
        int interruptions = 0;


        for (int i = 0; i< tests ; i++){

            if (r.nextBoolean()){
                dispatcher.interruptDispatching();
                interruptions++;
            } else {
                dispatcher.continueDispatching();
                interruptions--;
                if (interruptions < 0) {
                    interruptions = 0;
                }
            }

            if (interruptions > 0){
                assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();
            } else {
                assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();
            }
        }

        for (;interruptions > 1; interruptions --){
            dispatcher.continueDispatching();
            assertThat(dispatchingQueue.isDispatchingEnabled()).isFalse();
        }

        dispatcher.continueDispatching();
        assertThat(dispatchingQueue.isDispatchingEnabled()).isTrue();


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
