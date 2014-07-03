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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static com.squareup.picasso.BitmapHunter.forRequest;
import static com.squareup.picasso.Utils.OWNER_DISPATCHER;
import static com.squareup.picasso.Utils.VERB_BATCHED;
import static com.squareup.picasso.Utils.VERB_CANCELED;
import static com.squareup.picasso.Utils.VERB_DELIVERED;
import static com.squareup.picasso.Utils.VERB_ENQUEUED;
import static com.squareup.picasso.Utils.VERB_IGNORED;
import static com.squareup.picasso.Utils.VERB_REPLAYING;
import static com.squareup.picasso.Utils.VERB_RETRYING;
import static com.squareup.picasso.Utils.getLogIdsForHunter;
import static com.squareup.picasso.Utils.getService;
import static com.squareup.picasso.Utils.hasPermission;
import static com.squareup.picasso.Utils.log;

class Dispatcher {
  static final int REQUEST_SUBMIT = 1;
  static final int REQUEST_CANCEL = 2;
  static final int REQUEST_GCED = 3;
  static final int HUNTER_COMPLETE = 4;
  static final int HUNTER_RETRY = 5;
  static final int HUNTER_DECODE_FAILED = 6;
  static final int HUNTER_DELAY_NEXT_BATCH = 7;
  static final int HUNTER_BATCH_COMPLETE = 8;
  static final int NETWORK_STATE_CHANGE = 9;
  static final int AIRPLANE_MODE_CHANGE = 10;
  private static final int RETRY_DELAY = 500;
  private static final int AIRPLANE_MODE_ON = 1;
  private static final int AIRPLANE_MODE_OFF = 0;
  private static final String DISPATCHER_THREAD_NAME = "Dispatcher";
  private static final int BATCH_DELAY = 200; // ms

  final DispatcherThread dispatcherThread;
  final Context context;
  final ExecutorService service;
  final Downloader downloader;
  final Map<String, BitmapHunter> hunterMap;
  final Map<Object, Action> failedActions;
  final Handler handler;
  final Handler mainThreadHandler;
  final Cache cache;
  final Stats stats;
  final List<BitmapHunter> batch;
  final NetworkBroadcastReceiver receiver;
  final boolean scansNetworkChanges;
  final DispatchingQueue dispatchingQueue;

  boolean airplaneMode;

  Dispatcher(Context context, ExecutorService service, Handler mainThreadHandler,
      Downloader downloader, Cache cache, Stats stats) {
    this.dispatcherThread = new DispatcherThread();
    this.dispatcherThread.start();
    this.context = context;
    this.service = service;
    this.hunterMap = new LinkedHashMap<String, BitmapHunter>();
    this.failedActions = new WeakHashMap<Object, Action>();
    this.handler = new DispatcherHandler(dispatcherThread.getLooper(), this);
    this.dispatchingQueue = new DispatchingQueue(handler);
    this.downloader = downloader;
    this.mainThreadHandler = mainThreadHandler;
    this.cache = cache;
    this.stats = stats;
    this.batch = new ArrayList<BitmapHunter>(4);
    this.airplaneMode = Utils.isAirplaneModeOn(this.context);
    this.scansNetworkChanges = hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
    this.receiver = new NetworkBroadcastReceiver(this);
    receiver.register();
  }

  void shutdown() {
    service.shutdown();
    dispatcherThread.quit();
    receiver.unregister();
    dispatchingQueue.clear();
  }

  void dispatchSubmit(Action action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, action));
  }

  void dispatchCancel(Action action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_CANCEL, action));
  }

  void dispatchComplete(BitmapHunter hunter) {
    dispatchingQueue.dispatchComplete(hunter);
  }

  void dispatchRetry(BitmapHunter hunter) {
    handler.sendMessageDelayed(handler.obtainMessage(HUNTER_RETRY, hunter), RETRY_DELAY);
  }

  void dispatchFailed(BitmapHunter hunter) {
    dispatchingQueue.dispatchComplete(hunter);
  }

  void dispatchNetworkStateChange(NetworkInfo info) {
    handler.sendMessage(handler.obtainMessage(NETWORK_STATE_CHANGE, info));
  }

  void dispatchAirplaneModeChange(boolean airplaneMode) {
    handler.sendMessage(handler.obtainMessage(AIRPLANE_MODE_CHANGE,
        airplaneMode ? AIRPLANE_MODE_ON : AIRPLANE_MODE_OFF, 0));
  }

  void performSubmit(Action action) {
    BitmapHunter hunter = hunterMap.get(action.getKey());
    if (hunter != null) {
      hunter.attach(action);
      return;
    }

    if (service.isShutdown()) {
      if (action.getPicasso().loggingEnabled) {
        log(OWNER_DISPATCHER, VERB_IGNORED, action.request.logId(), "because shut down");
      }
      return;
    }

    hunter = forRequest(context, action.getPicasso(), this, cache, stats, action, downloader);
    hunter.future = service.submit(hunter);
    hunterMap.put(action.getKey(), hunter);
    failedActions.remove(action.getTarget());

    if (action.getPicasso().loggingEnabled) {
      log(OWNER_DISPATCHER, VERB_ENQUEUED, action.request.logId());
    }
  }

  void performCancel(Action action) {
    String key = action.getKey();
    BitmapHunter hunter = hunterMap.get(key);
    if (hunter != null) {
      hunter.detach(action);
      if (hunter.cancel()) {
        hunterMap.remove(key);
        dispatchingQueue.dequeue(hunter);
        if (action.getPicasso().loggingEnabled) {
          log(OWNER_DISPATCHER, VERB_CANCELED, action.getRequest().logId());
        }
      }
    }
    Action remove = failedActions.remove(action.getTarget());
    if (remove != null && remove.getPicasso().loggingEnabled) {
      log(OWNER_DISPATCHER, VERB_CANCELED, remove.getRequest().logId(), "from replaying");
    }
  }

  void performRetry(BitmapHunter hunter) {
    if (hunter.isCancelled()) return;

    if (service.isShutdown()) {
      performError(hunter, false);
      return;
    }

    NetworkInfo networkInfo = null;
    if (scansNetworkChanges) {
      ConnectivityManager connectivityManager = getService(context, CONNECTIVITY_SERVICE);
      networkInfo = connectivityManager.getActiveNetworkInfo();
    }

    boolean hasConnectivity = networkInfo != null && networkInfo.isConnected();
    boolean shouldRetryHunter = hunter.shouldRetry(airplaneMode, networkInfo);
    boolean supportsReplay = hunter.supportsReplay();

    if (!shouldRetryHunter) {
      // Mark for replay only if we observe network info changes and support replay.
      boolean willReplay = scansNetworkChanges && supportsReplay;
      performError(hunter, willReplay);
      if (willReplay) {
        markForReplay(hunter);
      }
      return;
    }

    // If we don't scan for network changes (missing permission) or if we have connectivity, retry.
    if (!scansNetworkChanges || hasConnectivity) {
      if (hunter.getPicasso().loggingEnabled) {
        log(OWNER_DISPATCHER, VERB_RETRYING, getLogIdsForHunter(hunter));
      }
      hunter.future = service.submit(hunter);
      return;
    }

    performError(hunter, supportsReplay);

    if (supportsReplay) {
      markForReplay(hunter);
    }
  }

  void performComplete(BitmapHunter hunter) {
    if (hunter.getResult() == null) {
      performError(hunter, false);
      return;
    }

    if (!hunter.shouldSkipMemoryCache()) {
      cache.set(hunter.getKey(), hunter.getResult());
    }
    hunterMap.remove(hunter.getKey());
    batch(hunter);
    if (hunter.getPicasso().loggingEnabled) {
      log(OWNER_DISPATCHER, VERB_BATCHED, getLogIdsForHunter(hunter), "for completion");
    }
  }

  void performBatchComplete() {
    List<BitmapHunter> copy = new ArrayList<BitmapHunter>(batch);
    batch.clear();
    mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(HUNTER_BATCH_COMPLETE, copy));
    logBatch(copy);
  }

  void performError(BitmapHunter hunter, boolean willReplay) {
    if (hunter.getPicasso().loggingEnabled) {
      log(OWNER_DISPATCHER, VERB_BATCHED, getLogIdsForHunter(hunter),
          "for error" + (willReplay ? " (will replay)" : ""));
    }
    hunterMap.remove(hunter.getKey());
    batch(hunter);
  }

  void performAirplaneModeChange(boolean airplaneMode) {
    this.airplaneMode = airplaneMode;
  }

  void performNetworkStateChange(NetworkInfo info) {
    if (service instanceof PicassoExecutorService) {
      ((PicassoExecutorService) service).adjustThreadCount(info);
    }
    // Intentionally check only if isConnected() here before we flush out failed actions.
    if (info != null && info.isConnected()) {
      flushFailedActions();
    }
  }

  private void flushFailedActions() {
    if (!failedActions.isEmpty()) {
      Iterator<Action> iterator = failedActions.values().iterator();
      while (iterator.hasNext()) {
        Action action = iterator.next();
        iterator.remove();
        if (action.getPicasso().loggingEnabled) {
          log(OWNER_DISPATCHER, VERB_REPLAYING, action.getRequest().logId());
        }
        performSubmit(action);
      }
    }
  }

  private void markForReplay(BitmapHunter hunter) {
    Action action = hunter.getAction();
    if (action != null) {
      markForReplay(action);
    }
    List<Action> joined = hunter.getActions();
    if (joined != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, n = joined.size(); i < n; i++) {
        Action join = joined.get(i);
        markForReplay(join);
      }
    }
  }

  private void markForReplay(Action action) {
    Object target = action.getTarget();
    if (target != null) {
      action.willReplay = true;
      failedActions.put(target, action);
    }
  }

  private void batch(BitmapHunter hunter) {
    if (hunter.isCancelled()) {
      return;
    }
    batch.add(hunter);
    if (!handler.hasMessages(HUNTER_DELAY_NEXT_BATCH)) {
      handler.sendEmptyMessageDelayed(HUNTER_DELAY_NEXT_BATCH, BATCH_DELAY);
    }
  }

  public void interruptDispatching() {

    dispatchingQueue.interruptDispatching();
    if (service instanceof PicassoExecutorService) {
      ((PicassoExecutorService) service).pause();
    }
  }

  public void continueDispatching() {

    dispatchingQueue.continueDispatching();
    if (service instanceof PicassoExecutorService) {
      ((PicassoExecutorService) service).resume();
    }
  }

  private void logBatch(List<BitmapHunter> copy) {
    if (copy == null || copy.isEmpty()) return;
    BitmapHunter hunter = copy.get(0);
    Picasso picasso = hunter.getPicasso();
    if (picasso.loggingEnabled) {
      StringBuilder builder = new StringBuilder();
      for (BitmapHunter bitmapHunter : copy) {
        if (builder.length() > 0) builder.append(", ");
        builder.append(Utils.getLogIdsForHunter(bitmapHunter));
      }
      log(OWNER_DISPATCHER, VERB_DELIVERED, builder.toString());
    }
  }

  private static class DispatcherHandler extends Handler {
    private final Dispatcher dispatcher;

    public DispatcherHandler(Looper looper, Dispatcher dispatcher) {
      super(looper);
      this.dispatcher = dispatcher;
    }

    @Override public void handleMessage(final Message msg) {
      switch (msg.what) {
        case REQUEST_SUBMIT: {
          Action action = (Action) msg.obj;
          dispatcher.performSubmit(action);
          break;
        }
        case REQUEST_CANCEL: {
          Action action = (Action) msg.obj;
          dispatcher.performCancel(action);
          break;
        }
        case HUNTER_COMPLETE: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performComplete(hunter);
          break;
        }
        case HUNTER_RETRY: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performRetry(hunter);
          break;
        }
        case HUNTER_DECODE_FAILED: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performError(hunter, false);
          break;
        }
        case HUNTER_DELAY_NEXT_BATCH: {
          dispatcher.performBatchComplete();
          break;
        }
        case NETWORK_STATE_CHANGE: {
          NetworkInfo info = (NetworkInfo) msg.obj;
          dispatcher.performNetworkStateChange(info);
          break;
        }
        case AIRPLANE_MODE_CHANGE: {
          dispatcher.performAirplaneModeChange(msg.arg1 == AIRPLANE_MODE_ON);
          break;
        }
        default:
          Picasso.HANDLER.post(new Runnable() {
            @Override public void run() {
              throw new AssertionError("Unknown handler message received: " + msg.what);
            }
          });
      }
    }
  }

  static class DispatcherThread extends HandlerThread {
    DispatcherThread() {
      super(Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
    }
  }

  static class NetworkBroadcastReceiver extends BroadcastReceiver {
    static final String EXTRA_AIRPLANE_STATE = "state";

    private final Dispatcher dispatcher;

    NetworkBroadcastReceiver(Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    void register() {
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
      if (dispatcher.scansNetworkChanges) {
        filter.addAction(CONNECTIVITY_ACTION);
      }
      dispatcher.context.registerReceiver(this, filter);
    }

    void unregister() {
      dispatcher.context.unregisterReceiver(this);
    }

    @Override public void onReceive(Context context, Intent intent) {
      // On some versions of Android this may be called with a null Intent,
      // also without extras (getExtras() == null), in such case we use defaults.
      if (intent == null) {
        return;
      }
      final String action = intent.getAction();
      if (ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
        if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
          return; // No airplane state, ignore it. Should we query Utils.isAirplaneModeOn?
        }
        dispatcher.dispatchAirplaneModeChange(intent.getBooleanExtra(EXTRA_AIRPLANE_STATE, false));
      } else if (CONNECTIVITY_ACTION.equals(action)) {
        ConnectivityManager connectivityManager = getService(context, CONNECTIVITY_SERVICE);
        dispatcher.dispatchNetworkStateChange(connectivityManager.getActiveNetworkInfo());
      }
    }
  }
}
