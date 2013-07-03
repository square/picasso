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

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP;
import static android.media.ExifInterface.ORIENTATION_NORMAL;
import static android.media.ExifInterface.ORIENTATION_ROTATE_180;
import static android.media.ExifInterface.ORIENTATION_ROTATE_270;
import static android.media.ExifInterface.ORIENTATION_ROTATE_90;
import static android.media.ExifInterface.TAG_ORIENTATION;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB_MR1;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.provider.ContactsContract.Contacts.openContactPhotoInputStream;

final class Utils {
  static final String THREAD_PREFIX = "Picasso-";
  static final String THREAD_IDLE_NAME = THREAD_PREFIX + "Idle";
  static final int DEFAULT_READ_TIMEOUT = 20 * 1000; // 20s
  static final int DEFAULT_CONNECT_TIMEOUT = 15 * 1000; // 15s
  private static final String PICASSO_CACHE = "picasso-cache";
  private static final int KEY_PADDING = 50; // Determined by exact science.
  private static final int MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024; // 5MB
  private static final int MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB
  private static final int MAX_MEM_CACHE_SIZE = 20 * 1024 * 1024; // 20MB
  private static final String[] CONTENT_ORIENTATION =
      new String[] {MediaStore.Images.ImageColumns.ORIENTATION};

  private Utils() {
    // No instances.
  }

  static int getContentProviderExifRotation(ContentResolver contentResolver, Uri uri) {
    Cursor cursor = null;
    try {
      cursor = contentResolver.query(uri, CONTENT_ORIENTATION, null, null, null);
      if (cursor == null || !cursor.moveToFirst()) {
        return 0;
      }
      return cursor.getInt(0);
    } catch (RuntimeException ignored) {
      // If the orientation column doesn't exist, assume no rotation.
      return 0;
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

  static String createKey(Uri uri, int resourceId, PicassoBitmapOptions options,
      List<Transformation> transformations) {
    StringBuilder builder;

    if (uri != null) {
      String path = uri.toString();
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
      if (options.centerInside) {
        builder.append("centerInside\n");
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

  static void closeQuietly(InputStream is) {
    try {
      is.close();
    } catch (IOException ignored) {
    }
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

  static Downloader createDefaultDownloader(Context context) {
    try {
      Class.forName("com.squareup.okhttp.OkHttpClient");
      return OkHttpLoaderCreator.create(context);
    } catch (ClassNotFoundException e) {
      return new UrlConnectionDownloader(context);
    }
  }

  static File createDefaultCacheDir(Context context) {
    File cache = new File(context.getApplicationContext().getCacheDir(), PICASSO_CACHE);
    if (!cache.exists()) {
      cache.mkdirs();
    }
    return cache;
  }

  static int calculateDiskCacheSize(File dir) {
    int size = MIN_DISK_CACHE_SIZE;

    try {
      StatFs statFs = new StatFs(dir.getAbsolutePath());
      int available = statFs.getBlockCount() * statFs.getBlockSize();
      // Target 2% of the total space.
      size = available / 50;
    } catch (IllegalArgumentException ignored) {
    }

    // Bound inside min/max size for disk cache.
    return Math.max(Math.min(size, MAX_DISK_CACHE_SIZE), MIN_DISK_CACHE_SIZE);
  }

  static int calculateMemoryCacheSize(Context context) {
    ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
    boolean largeHeap = (context.getApplicationInfo().flags & FLAG_LARGE_HEAP) != 0;
    int memoryClass = am.getMemoryClass();
    if (largeHeap && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      memoryClass = ActivityManagerHoneycomb.getLargeMemoryClass(am);
    }
    // Target 15% of the available RAM.
    int size = 1024 * 1024 * memoryClass / 7;
    // Bound to max size for mem cache.
    return Math.min(size, MAX_MEM_CACHE_SIZE);
  }

  public static InputStream getContactPhotoStream(ContentResolver contentResolver, Uri uri) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (uri.toString().startsWith(ContactsContract.Contacts.CONTENT_LOOKUP_URI.toString())) {
        uri = ContactsContract.Contacts.lookupContact(contentResolver, uri);
        if (uri == null) {
          return null;
        }
      }
      return openContactPhotoInputStream(contentResolver, uri);
    } else {
      return ContactPhotoStreamIcs.get(contentResolver, uri);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private static class ActivityManagerHoneycomb {
    static int getLargeMemoryClass(ActivityManager activityManager) {
      return activityManager.getLargeMemoryClass();
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

  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
  private static class BitmapHoneycombMR1 {
    static int getByteCount(Bitmap bitmap) {
      return bitmap.getByteCount();
    }
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private static class ContactPhotoStreamIcs {
    static InputStream get(ContentResolver contentResolver, Uri uri) {
      return openContactPhotoInputStream(contentResolver, uri, true);
    }
  }

  private static class OkHttpLoaderCreator {
    static Downloader create(Context context) {
      return new OkHttpDownloader(context);
    }
  }
}
