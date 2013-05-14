package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import static android.media.ExifInterface.ORIENTATION_NORMAL;
import static android.media.ExifInterface.ORIENTATION_ROTATE_180;
import static android.media.ExifInterface.ORIENTATION_ROTATE_270;
import static android.media.ExifInterface.ORIENTATION_ROTATE_90;
import static android.media.ExifInterface.TAG_ORIENTATION;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB_MR1;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

final class Utils {
  static final String THREAD_PREFIX = "Picasso-";
  static final String THREAD_IDLE_NAME = THREAD_PREFIX + "Idle";
  private static final int KEY_PADDING = 50; // Determined by exact science.
  private static final String[] CONTENT_ORIENTATION = new String[] {
      MediaStore.Images.ImageColumns.ORIENTATION
  };

  private Utils() {
    // No instances.
  }

  static int getContentProviderExifRotation(Uri uri, ContentResolver contentResolver) {
    Cursor cursor = null;
    try {
      cursor = contentResolver.query(uri, CONTENT_ORIENTATION, null, null, null);
      if (cursor == null || !cursor.moveToFirst()) {
        return 0;
      }
      return cursor.getInt(0);
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  static int getBitmapBytes(Bitmap bitmap) {
    int result;
    if (SDK_INT >= HONEYCOMB_MR1) {
      result = BitmapHoneycombMR1.getByteCount(bitmap);
    } else {
      result = bitmap.getRowBytes() * bitmap.getHeight();
    }
    if (result < 0) {
      throw new IllegalStateException("Negative size: " + bitmap);
    }
    return result;
  }

  static int getFileExifRotation(String path) throws IOException {
    ExifInterface exifInterface = new ExifInterface(path);
    int orientation = exifInterface.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
    switch (orientation) {
      case ORIENTATION_ROTATE_90:
        return 90;
      case ORIENTATION_ROTATE_180:
        return 180;
      case ORIENTATION_ROTATE_270:
        return 270;
      default:
        return 0;
    }
  }

  static void checkNotMain() {
    if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
      throw new IllegalStateException("Method call should not happen from the main thread.");
    }
  }

  static String createKey(Request request) {
    return createKey(request.path, request.resourceId, request.options, request.transformations);
  }

  static String createKey(String path, int resourceId, PicassoBitmapOptions options,
      List<Transformation> transformations) {
    StringBuilder builder;

    if (path != null) {
      builder = new StringBuilder(path.length() + KEY_PADDING);
      builder.append(path);
    } else {
      builder = new StringBuilder(KEY_PADDING);
      builder.append(resourceId);
    }
    builder.append('\n');

    if (options != null) {
      float targetRotation = options.targetRotation;
      if (targetRotation != 0) {
        builder.append("rotation:").append(targetRotation);
        if (options.hasRotationPivot) {
          builder.append('@').append(options.targetPivotX).append('x').append(options.targetPivotY);
        }
        builder.append('\n');
      }
      int targetWidth = options.targetWidth;
      int targetHeight = options.targetHeight;
      if (targetWidth != 0) {
        builder.append("resize:").append(targetWidth).append('x').append(targetHeight);
        builder.append('\n');
      }
      if (options.centerCrop) {
        builder.append("centerCrop\n");
      }
      float targetScaleX = options.targetScaleX;
      float targetScaleY = options.targetScaleY;
      if (targetScaleX != 0) {
        builder.append("scale:").append(targetScaleX).append('x').append(targetScaleY);
        builder.append('\n');
      }
    }

    if (transformations != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, count = transformations.size(); i < count; i++) {
        builder.append(transformations.get(i).key());
        builder.append('\n');
      }
    }

    return builder.toString();
  }

  static void calculateInSampleSize(PicassoBitmapOptions options) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    final int reqHeight = options.targetHeight;
    final int reqWidth = options.targetWidth;
    int sampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
      final int heightRatio = Math.round((float) height / (float) reqHeight);
      final int widthRatio = Math.round((float) width / (float) reqWidth);
      sampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
    }

    options.inSampleSize = sampleSize;
    options.inJustDecodeBounds = false;
  }

  /** Returns {@code true} if header indicates the response body was loaded from the disk cache. */
  static boolean parseResponseSourceHeader(String header) {
    if (header == null) {
      return false;
    }
    String[] parts = header.split(" ", 2);
    if ("CACHE".equals(parts[0])) {
      return true;
    }
    if (parts.length == 1) {
      return false;
    }
    try {
      return "CONDITIONAL_CACHE".equals(parts[0]) && Integer.parseInt(parts[1]) == 304;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  static Loader createDefaultLoader(Context context) {
    try {
      Class.forName("com.squareup.okhttp.OkHttpClient");
      return OkHttpLoaderCreator.create(context);
    } catch (ClassNotFoundException e) {
      return new UrlConnectionLoader(context);
    }
  }

  static class PicassoThreadFactory implements ThreadFactory {
    @SuppressWarnings("NullableProblems")
    public Thread newThread(Runnable r) {
      return new PicassoThread(r);
    }
  }

  private static class PicassoThread extends Thread {
    public PicassoThread(Runnable r) {
      super(r);
    }

    @Override public void run() {
      Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND);
      super.run();
    }
  }

  private static class BitmapHoneycombMR1 {
    static int getByteCount(Bitmap bitmap) {
      return bitmap.getByteCount();
    }
  }

  private static class OkHttpLoaderCreator {
    static Loader create(Context context) {
      return new OkHttpLoader(context);
    }
  }
}
