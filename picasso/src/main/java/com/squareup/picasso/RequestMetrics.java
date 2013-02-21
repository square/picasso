package com.squareup.picasso;

import android.graphics.Color;

public class RequestMetrics {

  static final int LOADED_FROM_MEM = 1;
  static final int LOADED_FROM_DISK = 2;
  static final int LOADED_FROM_NETWORK = 3;

  long createdTime;
  long executedTime;
  long keyCreationTime;
  long networkLoadTime;
  long cacheLoadTime;
  long executorWaitTime;

  int loadedFrom;

  public long getExecutorWaitTime() {
    if (executedTime == 0) {
      return -1;
    }
    return executedTime - createdTime;
  }

  public int getLoadedFrom() {
    return loadedFrom;
  }

  static int getColorCodeForCacheHit(int loadedFrom) {
    switch (loadedFrom) {
      case LOADED_FROM_MEM:
        return Color.GREEN;
      case LOADED_FROM_DISK:
        return Color.YELLOW;
      case LOADED_FROM_NETWORK:
        return Color.RED;
      default:
        throw new IllegalArgumentException("WTF DID YOU JUST PASS?");
    }
  }
}
