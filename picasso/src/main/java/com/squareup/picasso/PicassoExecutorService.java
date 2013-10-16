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

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The default {@link java.util.concurrent.ExecutorService} used for new {@link Picasso} instances.
 * <p/>
 * Exists as a custom type so that we can differentiate the use of defaults versus a user-supplied
 * instance.
 */
class PicassoExecutorService extends ThreadPoolExecutor {
	private static final int CORE_POOL_SIZE = 3;
	private static final int MAX_CORE_POOL_SIZE = 5;

  PicassoExecutorService() {
    super(CORE_POOL_SIZE, MAX_CORE_POOL_SIZE, 5, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>(), new Utils.PicassoThreadFactory());
  }

  void adjustThreadCount(NetworkInfo info) {
    if (info == null || !info.isConnectedOrConnecting()) {
      setThreadCount(CORE_POOL_SIZE, MAX_CORE_POOL_SIZE);
      return;
    }
    switch (info.getType()) {
      case ConnectivityManager.TYPE_WIFI:
      case ConnectivityManager.TYPE_WIMAX:
      case ConnectivityManager.TYPE_ETHERNET:
        setThreadCount(4, 6);
        break;
      case ConnectivityManager.TYPE_MOBILE:
        switch (info.getSubtype()) {
          case TelephonyManager.NETWORK_TYPE_LTE:  // 4G
          case TelephonyManager.NETWORK_TYPE_HSPAP:
          case TelephonyManager.NETWORK_TYPE_EHRPD:
            setThreadCount(3, 5);
            break;
          case TelephonyManager.NETWORK_TYPE_UMTS: // 3G
          case TelephonyManager.NETWORK_TYPE_CDMA:
          case TelephonyManager.NETWORK_TYPE_EVDO_0:
          case TelephonyManager.NETWORK_TYPE_EVDO_A:
          case TelephonyManager.NETWORK_TYPE_EVDO_B:
            setThreadCount(2, 3);
            break;
          case TelephonyManager.NETWORK_TYPE_GPRS: // 2G
          case TelephonyManager.NETWORK_TYPE_EDGE:
            setThreadCount(1, 2);
            break;
          default:
        	  setThreadCount(CORE_POOL_SIZE, MAX_CORE_POOL_SIZE);
        }
        break;
      default:
    	  setThreadCount(CORE_POOL_SIZE, MAX_CORE_POOL_SIZE);
    }
  }

  private void setThreadCount(int threadCount, int maxThreadCount) {
    setCorePoolSize(threadCount);
    setMaximumPoolSize(threadCount);
  }
}
