package com.squareup.picasso;

import android.graphics.Color;

public class RequestMetrics {

  enum LoadedFrom {
    MEM, DISK, NETWORK
  }

  long createdTime;
  long executedTime;
  long keyCreationTime;
  long networkLoadTime;
  long cacheLoadTime;
  long executorWaitTime;

  LoadedFrom loadedFrom;

  public long getExecutorWaitTime() {
    if (executedTime == 0) {
      return -1;
    }
    return executedTime - createdTime;
  }

  public LoadedFrom getLoadedFrom() {
    return loadedFrom;
  }

  static int getColorCodeForCacheHit(LoadedFrom loadedFrom) {
    switch (loadedFrom) {
      case MEM:
        return Color.GREEN;
      case DISK:
        return Color.YELLOW;
      case NETWORK:
        return Color.RED;
      default:
        throw new AssertionError("Unable to map color to source " + loadedFrom);
    }
  }
}
