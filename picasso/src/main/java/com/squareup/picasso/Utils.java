package com.squareup.picasso;

import android.os.Looper;

final class Utils {

  private Utils() {
    // No instances.
  }

  static void checkNotMain() {
    if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
      throw new IllegalStateException("Method call should not happen from the main thread.");
    }
  }
}
