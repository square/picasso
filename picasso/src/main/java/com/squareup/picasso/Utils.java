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
    return createKey(request.path, request.transformations, request.metrics);
  }

  static String createKey(String path, List<Transformation> transformations,
      RequestMetrics metrics) {
    long start = System.nanoTime();
    StringBuilder builder = new StringBuilder();
    builder.append(path);

    if (transformations != null && !transformations.isEmpty()) {
      if (transformations.size() == 1) {
        builder.append('|');
        builder.append(transformations.get(0).toString());
      } else {
        for (Transformation transformation : transformations) {
          builder.append('|');
          builder.append(transformation.toString());
        }
      }
    }

    if (metrics != null) {
      metrics.keyCreationTime = System.nanoTime() - start;
    }

    // TODO Support bitmap options?
    return builder.toString();
  }
}
