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
import android.util.Log;
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

class Dispatcher {
  private static final int RETRY_DELAY = 500;
  private static final int AIRPLANE_MODE_ON = 1;
  private static final int AIRPLANE_MODE_OFF = 0;

  static final int REQUEST_SUBMIT = 1;
  static final int REQUEST_CANCEL = 2;
  static final int REQUEST_GCED = 3;
  static final int HUNTER_COMPLETE = 4;
  static final int HUNTER_RETRY = 5;
  static final int HUNTER_FAILED = 6;
  static final int HUNTER_DECODE_FAILED = 7;
  static final int NETWORK_STATE_CHANGE = 8;
  static final int AIRPLANE_MODE_CHANGE = 9;

  private static final String DISPATCHER_THREAD_NAME = "Dispatcher";

  final Context context;
  final ExecutorService service;
  final Downloader downloader;
  final Map<String, BitmapHunter> hunterMap;
  final Map<Object, Request> failedRequests;
  final Handler handler;
  final Handler mainThreadHandler;
  final Cache cache;

  boolean airplaneMode;

  Dispatcher(Context context, ExecutorService service, Handler mainThreadHandler,
      Downloader downloader, Cache cache) {
    DispatcherThread thread = new DispatcherThread();
    thread.start();
    this.context = context;
    this.service = service;
    this.hunterMap = new LinkedHashMap<String, BitmapHunter>();
    this.failedRequests = new WeakHashMap<Object, Request>();
    this.handler = new DispatcherHandler(thread.getLooper());
    this.downloader = downloader;
    this.mainThreadHandler = mainThreadHandler;
    this.cache = cache;
    this.airplaneMode = Utils.isAirplaneModeOn(context);

    if (Utils.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      if (service instanceof PicassoExecutorService) {
        NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(context);
        receiver.register();
      }
    } else {
      Log.w("Picasso", "android.permission.ACCESS_NETWORK_STATE missing. Forcing fixed threads.");
    }
  }

  void dispatchSubmit(Request request) {
    handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, request));
  }

  void dispatchCancel(Request request) {
    handler.sendMessage(handler.obtainMessage(REQUEST_CANCEL, request));
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

  void performSubmit(Request request) {
    BitmapHunter hunter = hunterMap.get(request.getKey());
    if (hunter != null) {
      hunter.attach(request);
      return;
    }
    hunter =
        forRequest(context, request.getPicasso(), this, cache, request, downloader, airplaneMode);
    hunter.future = service.submit(hunter);
    hunterMap.put(hunter.getKey(), hunter);
  }

  void performCancel(Request request) {
    String key = request.getKey();
    BitmapHunter hunter = hunterMap.get(key);
    if (hunter != null) {
      hunter.detach(request);
      if (hunter.cancel()) {
        hunterMap.remove(key);
      }
    }
    failedRequests.remove(request.getTarget());
  }

  void performRetry(BitmapHunter hunter) {
    if (hunter.isCancelled()) return;

    if (hunter.retryCount > 0) {
      hunter.retryCount--;
      hunter.future = service.submit(hunter);
    } else {
      List<Request> requests = hunter.getRequests();
      for (Request request : requests) {
        Object target = request.getTarget();
        if (target != null) {
          failedRequests.put(request.getTarget(), request);
        }
      }
      performError(hunter);
    }
  }

  void performComplete(BitmapHunter hunter) {
    if (!hunter.shouldSkipCache()) {
      cache.set(hunter.getKey(), hunter.getResult());
    }
    hunterMap.remove(hunter.getKey());
    mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(HUNTER_COMPLETE, hunter));
  }

  void performError(BitmapHunter hunter) {
    hunterMap.remove(hunter.getKey());
    mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(HUNTER_FAILED, hunter));
  }

  void performAirplaneModeChange(boolean airplaneMode) {
    this.airplaneMode = airplaneMode;
  }

  void performNetworkStateChange(NetworkInfo info) {
    if (info != null && info.isConnectedOrConnecting()) {
      if (service instanceof PicassoExecutorService) {
        ((PicassoExecutorService) service).adjustThreadCount(info);
      }
    }
    flushFailedRequests();
  }

  private void flushFailedRequests() {
    if (!failedRequests.isEmpty()) {
      for (Request failed : failedRequests.values()) {
        performSubmit(failed);
      }
      failedRequests.clear();
    }
  }

  private class DispatcherHandler extends Handler {
    public DispatcherHandler(Looper looper) {
      super(looper);
    }

    @Override public void handleMessage(Message msg) {
      switch (msg.what) {
        case REQUEST_SUBMIT: {
          Request request = (Request) msg.obj;
          performSubmit(request);
          break;
        }
        case REQUEST_CANCEL: {
          Request request = (Request) msg.obj;
          performCancel(request);
          break;
        }
        case HUNTER_COMPLETE: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          performComplete(hunter);
          break;
        }
        case HUNTER_RETRY: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          performRetry(hunter);
          break;
        }
        case HUNTER_DECODE_FAILED: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          performError(hunter);
          break;
        }
        case NETWORK_STATE_CHANGE: {
          NetworkInfo info = (NetworkInfo) msg.obj;
          performNetworkStateChange(info);
          break;
        }
        case AIRPLANE_MODE_CHANGE: {
          performAirplaneModeChange(msg.arg1 == AIRPLANE_MODE_ON);
          break;
        }
        default:
          throw new AssertionError("Unknown handler message received: " + msg.what);
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
      this.connectivityManager =
          (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
    }

    void register() {
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
      filter.addAction(CONNECTIVITY_ACTION);
      context.registerReceiver(this, filter);
    }

    @Override public void onReceive(Context context, Intent intent) {
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
