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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static com.squareup.picasso.BitmapHunter.forRequest;

class Dispatcher {
  private static final int RETRY_DELAY = 500;
  private static final int AIRPLANE_MODE_ON = 1;
  private static final int AIRPLANE_MODE_OFF = 0;

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

  private static final String DISPATCHER_THREAD_NAME = "Dispatcher";
  private static final int BATCH_DELAY = 200; // ms

  final DispatcherThread dispatcherThread;
  final Context context;
  final ExecutorService service;
  final Downloader downloader;
  final Map<String, BitmapHunter> hunterMap;
  final Handler handler;
  final Handler mainThreadHandler;
  final Cache cache;
  final Stats stats;
  final List<BitmapHunter> batch;
  final NetworkBroadcastReceiver receiver;

  NetworkInfo networkInfo;
  boolean airplaneMode;

  Dispatcher(Context context, ExecutorService service, Handler mainThreadHandler,
      Downloader downloader, Cache cache, Stats stats) {
    this.dispatcherThread = new DispatcherThread();
    this.dispatcherThread.start();
    this.context = context;
    this.service = service;
    this.hunterMap = new LinkedHashMap<String, BitmapHunter>();
    this.handler = new DispatcherHandler(dispatcherThread.getLooper(), this);
    this.downloader = downloader;
    this.mainThreadHandler = mainThreadHandler;
    this.cache = cache;
    this.stats = stats;
    this.batch = new ArrayList<BitmapHunter>(4);
    this.airplaneMode = Utils.isAirplaneModeOn(this.context);
    this.receiver = new NetworkBroadcastReceiver(this.context);
    receiver.register();
  }

  void shutdown() {
    service.shutdown();
    dispatcherThread.quit();
    receiver.unregister();
  }

  void dispatchSubmit(Action action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, action));
  }

  void dispatchCancel(Action action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_CANCEL, action));
  }

  void dispatchComplete(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_COMPLETE, hunter));
  }

  void dispatchRetry(BitmapHunter hunter) {
    handler.sendMessageDelayed(handler.obtainMessage(HUNTER_RETRY, hunter), RETRY_DELAY);
  }

  void dispatchFailed(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_DECODE_FAILED, hunter));
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
      return;
    }

    hunter = forRequest(context, action.getPicasso(), this, cache, stats, action, downloader);
    hunter.future = service.submit(hunter);
    hunterMap.put(action.getKey(), hunter);
  }

  void performCancel(Action action) {
    String key = action.getKey();
    BitmapHunter hunter = hunterMap.get(key);
    if (hunter != null) {
      hunter.detach(action);
      if (hunter.cancel()) {
        hunterMap.remove(key);
      }
    }
  }

  void performRetry(BitmapHunter hunter) {
    if (hunter.isCancelled()) return;

    if (service.isShutdown()) {
      performError(hunter);
      return;
    }

    if (hunter.shouldRetry(airplaneMode, networkInfo)) {
      hunter.future = service.submit(hunter);
    } else {
      performError(hunter);
    }
  }

  void performComplete(BitmapHunter hunter) {
    if (!hunter.shouldSkipMemoryCache()) {
      cache.set(hunter.getKey(), hunter.getResult());
    }
    hunterMap.remove(hunter.getKey());
    batch(hunter);
  }

  void performBatchComplete() {
    List<BitmapHunter> copy = new ArrayList<BitmapHunter>(batch);
    batch.clear();
    mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(HUNTER_BATCH_COMPLETE, copy));
  }

  void performError(BitmapHunter hunter) {
    hunterMap.remove(hunter.getKey());
    batch(hunter);
  }

  void performAirplaneModeChange(boolean airplaneMode) {
    this.airplaneMode = airplaneMode;
  }

  void performNetworkStateChange(NetworkInfo info) {
    networkInfo = info;
    if (service instanceof PicassoExecutorService) {
      ((PicassoExecutorService) service).adjustThreadCount(info);
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
          dispatcher.performError(hunter);
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

  private class NetworkBroadcastReceiver extends BroadcastReceiver {
    private static final String EXTRA_AIRPLANE_STATE = "state";

    private final ConnectivityManager connectivityManager;

    NetworkBroadcastReceiver(Context context) {
      connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
    }

    void register() {
      boolean shouldScanState = service instanceof PicassoExecutorService && //
          Utils.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
      if (shouldScanState) {
        filter.addAction(CONNECTIVITY_ACTION);
      }
      context.registerReceiver(this, filter);
    }

    void unregister() {
      context.unregisterReceiver(this);
    }

    @Override public void onReceive(Context context, Intent intent) {
      // On some versions of Android this may be called with a null Intent
      if (null == intent) {
        return;
      }

      String action = intent.getAction();
      Bundle extras = intent.getExtras();

      if (ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
        dispatchAirplaneModeChange(extras.getBoolean(EXTRA_AIRPLANE_STATE, false));
      } else if (CONNECTIVITY_ACTION.equals(action)) {
        dispatchNetworkStateChange(connectivityManager.getActiveNetworkInfo());
      }
    }
  }
}
