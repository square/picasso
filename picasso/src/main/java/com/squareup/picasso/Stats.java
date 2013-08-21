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

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

class Stats {
  private static final int REQUESTED_COMPLETED = 0;
  private static final int CACHE_HIT = 1;
  private static final int CACHE_MISS = 2;
  private static final int BITMAP_DECODE_FINISHED = 3;
  private static final int BITMAP_TRANSFORMED_FINISHED = 4;

  private static final String STATS_THREAD_NAME = Utils.THREAD_PREFIX + "Stats";

  final HandlerThread statsThread;
  final Cache cache;
  final Handler handler;

  long cacheHits;
  long cacheMisses;
  long totalOriginalBitmapSize;
  long totalTransformedBitmapSize;
  long averageOriginalBitmapSize;
  long averageTransformedBitmapSize;
  int originalBitmapCount;
  int transformedBitmapCount;

  Stats(Cache cache) {
    this.cache = cache;
    this.statsThread = new HandlerThread(STATS_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
    this.statsThread.start();
    this.handler = new StatsHandler(statsThread.getLooper());
  }

  void dispatchBitmapDecoded(Bitmap bitmap) {
    processBitmap(bitmap, BITMAP_DECODE_FINISHED);
  }

  void dispatchBitmapTransformed(Bitmap bitmap) {
    processBitmap(bitmap, BITMAP_TRANSFORMED_FINISHED);
  }

  void dispatchCacheHit() {
    handler.sendEmptyMessage(CACHE_HIT);
  }

  void dispatchCacheMiss() {
    handler.sendEmptyMessage(CACHE_MISS);
  }

  void shutdown() {
    statsThread.quit();
  }

  void performCacheHit() {
    cacheHits++;
  }

  void performCacheMiss() {
    cacheMisses++;
  }

  void performBitmapDecoded(long size) {
    originalBitmapCount++;
    totalOriginalBitmapSize += size;
    averageOriginalBitmapSize = getAverage(originalBitmapCount, totalOriginalBitmapSize);
  }

  void performBitmapTransformed(long size) {
    transformedBitmapCount++;
    totalTransformedBitmapSize += size;
    averageTransformedBitmapSize =
        getAverage(originalBitmapCount, totalTransformedBitmapSize);
  }

  synchronized StatsSnapshot createSnapshot() {
    return new StatsSnapshot(cache.maxSize(), cache.size(), cacheHits, cacheMisses,
        totalOriginalBitmapSize, totalTransformedBitmapSize, averageOriginalBitmapSize,
        averageTransformedBitmapSize, originalBitmapCount, transformedBitmapCount,
        System.currentTimeMillis());
  }

  private void processBitmap(Bitmap bitmap, int what) {
    // Never send bitmaps to the handler as they could be recycled before we process them.
    int bitmapSize = Utils.getBitmapBytes(bitmap);
    handler.sendMessage(handler.obtainMessage(what, bitmapSize, 0));
  }

  private static long getAverage(int count, long totalSize) {
    return totalSize / count;
  }

  private class StatsHandler extends Handler {

    public StatsHandler(Looper looper) {
      super(looper);
    }

    @Override public void handleMessage(final Message msg) {
      synchronized (Stats.this) {
        switch (msg.what) {
          case CACHE_HIT:
            performCacheHit();
            break;
          case CACHE_MISS:
            performCacheMiss();
            break;
          case BITMAP_DECODE_FINISHED:
            performBitmapDecoded(msg.arg1);
            break;
          case BITMAP_TRANSFORMED_FINISHED:
            performBitmapTransformed(msg.arg1);
            break;
          case REQUESTED_COMPLETED:
            break;
          default:
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(new Runnable() {
              @Override public void run() {
                throw new AssertionError("Unhandled stats message." + msg.what);
              }
            });
        }
      }
    }
  }
}