package com.squareup.picasso;

import android.os.Looper;
import java.util.List;

final class Utils {

  private Utils() {
    // No instances.
  }

  static void checkNotMain() {
    if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
      throw new IllegalStateException("Method call should not happen from the main thread.");
    }
  }

  static String createKey(Request request) {
    long start = System.nanoTime();
    StringBuilder builder = new StringBuilder();
    builder.append(request.path);

    List<Transformation> transformations = request.transformations;
    if (!transformations.isEmpty()) {
      for (Transformation transformation : transformations) {
        builder.append('|');
        builder.append(transformation.toString());
      }
    }

    RequestMetrics metrics = request.metrics;
    if (metrics != null) {
      metrics.keyCreationTime = System.nanoTime() - start;
    }

    // TODO Support bitmap options?
    return builder.toString();
  }
}
