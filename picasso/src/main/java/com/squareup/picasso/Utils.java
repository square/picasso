package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
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

  static Bitmap applyDebugColorFilter(Bitmap source, int loadedFrom) {
    int color = RequestMetrics.getColorCodeForCacheHit(loadedFrom);

    ColorFilter filter = new LightingColorFilter(color, 1);

    Paint paint = new Paint();
    paint.setColorFilter(filter);

    Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), source.getConfig());

    Canvas canvas = new Canvas(output);
    canvas.drawBitmap(source, 0, 0, paint);

    // Do not recycle source bitmap here. This is the image that is stored inside the cache and can
    // be used again when debugging is off.
    // source.recycle();
    return output;
  }
}
