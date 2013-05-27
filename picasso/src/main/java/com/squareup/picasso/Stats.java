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
    HandlerThread statsThread = new HandlerThread(STATS_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
    statsThread.start();
    handler = new StatsHandler(statsThread.getLooper());
  }

  void bitmapDecoded(Bitmap bitmap) {
    processBitmap(bitmap, BITMAP_DECODE_FINISHED);
  }

  void bitmapTransformed(Bitmap bitmap) {
    processBitmap(bitmap, BITMAP_TRANSFORMED_FINISHED);
  }

  void cacheHit() {
    handler.sendEmptyMessage(CACHE_HIT);
  }

  void cacheMiss() {
    handler.sendEmptyMessage(CACHE_MISS);
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
            cacheHits++;
            break;
          case CACHE_MISS:
            cacheMisses++;
            break;
          case BITMAP_DECODE_FINISHED:
            originalBitmapCount++;
            totalOriginalBitmapSize += msg.arg1;
            averageOriginalBitmapSize = getAverage(originalBitmapCount, totalOriginalBitmapSize);
            break;
          case BITMAP_TRANSFORMED_FINISHED:
            transformedBitmapCount++;
            totalTransformedBitmapSize += msg.arg1;
            averageTransformedBitmapSize =
                getAverage(originalBitmapCount, totalTransformedBitmapSize);
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
