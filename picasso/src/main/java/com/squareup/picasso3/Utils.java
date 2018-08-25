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
package com.squareup.picasso3;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.StatFs;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import okio.BufferedSource;
import okio.ByteString;

import static android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static com.squareup.picasso3.Picasso.TAG;
import static java.lang.String.format;

final class Utils {
  static final String THREAD_PREFIX = "Picasso-";
  static final String THREAD_IDLE_NAME = THREAD_PREFIX + "Idle";
  private static final String PICASSO_CACHE = "picasso-cache";
  private static final int MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024; // 5MB
  private static final int MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB
  static final int THREAD_LEAK_CLEANING_MS = 1000;

  /** Thread confined to main thread for key creation. */
  static final StringBuilder MAIN_THREAD_KEY_BUILDER = new StringBuilder();

  /** Logging */
  static final String OWNER_MAIN = "Main";
  static final String OWNER_DISPATCHER = "Dispatcher";
  static final String OWNER_HUNTER = "Hunter";
  static final String VERB_CREATED = "created";
  static final String VERB_CHANGED = "changed";
  static final String VERB_IGNORED = "ignored";
  static final String VERB_ENQUEUED = "enqueued";
  static final String VERB_CANCELED = "canceled";
  static final String VERB_RETRYING = "retrying";
  static final String VERB_EXECUTING = "executing";
  static final String VERB_DECODED = "decoded";
  static final String VERB_TRANSFORMED = "transformed";
  static final String VERB_JOINED = "joined";
  static final String VERB_REMOVED = "removed";
  static final String VERB_DELIVERED = "delivered";
  static final String VERB_REPLAYING = "replaying";
  static final String VERB_COMPLETED = "completed";
  static final String VERB_ERRORED = "errored";
  static final String VERB_PAUSED = "paused";
  static final String VERB_RESUMED = "resumed";

  /* WebP file header
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      'R'      |      'I'      |      'F'      |      'F'      |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                           File Size                           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      'W'      |      'E'      |      'B'      |      'P'      |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  */
  private static final ByteString WEBP_FILE_HEADER_RIFF = ByteString.encodeUtf8("RIFF");
  private static final ByteString WEBP_FILE_HEADER_WEBP = ByteString.encodeUtf8("WEBP");

  private Utils() {
    // No instances.
  }

  public static @NonNull <T> T checkNotNull(@Nullable T value, @Nullable String message) {
    if (value == null) {
      throw new NullPointerException(message);
    }
    return value;
  }

  static void checkNotMain() {
    if (isMain()) {
      throw new IllegalStateException("Method call should not happen from the main thread.");
    }
  }

  static void checkMain() {
    if (!isMain()) {
      throw new IllegalStateException("Method call should happen from the main thread.");
    }
  }

  static boolean isMain() {
    return Looper.getMainLooper().getThread() == Thread.currentThread();
  }

  static String getLogIdsForHunter(BitmapHunter hunter) {
    return getLogIdsForHunter(hunter, "");
  }

  static String getLogIdsForHunter(BitmapHunter hunter, String prefix) {
    StringBuilder builder = new StringBuilder(prefix);
    Action action = hunter.getAction();
    if (action != null) {
      builder.append(action.request.logId());
    }
    List<Action> actions = hunter.getActions();
    if (actions != null) {
      for (int i = 0, count = actions.size(); i < count; i++) {
        if (i > 0 || action != null) builder.append(", ");
        builder.append(actions.get(i).request.logId());
      }
    }
    return builder.toString();
  }

  static void log(String owner, String verb, String logId) {
    log(owner, verb, logId, "");
  }

  static void log(String owner, String verb, String logId, @Nullable String extras) {
    if (extras == null) {
      extras = "";
    }
    Log.d(TAG, format("%1$-11s %2$-12s %3$s %4$s", owner, verb, logId, extras));
  }

  static File createDefaultCacheDir(Context context) {
    File cache = new File(context.getApplicationContext().getCacheDir(), PICASSO_CACHE);
    if (!cache.exists()) {
      //noinspection ResultOfMethodCallIgnored
      cache.mkdirs();
    }
    return cache;
  }

  static long calculateDiskCacheSize(File dir) {
    long size = MIN_DISK_CACHE_SIZE;

    try {
      StatFs statFs = new StatFs(dir.getAbsolutePath());
      //noinspection deprecation
      long blockCount =
          SDK_INT < JELLY_BEAN_MR2 ? (long) statFs.getBlockCount() : statFs.getBlockCountLong();
      //noinspection deprecation
      long blockSize =
          SDK_INT < JELLY_BEAN_MR2 ? (long) statFs.getBlockSize() : statFs.getBlockSizeLong();
      long available = blockCount * blockSize;
      // Target 2% of the total space.
      size = available / 50;
    } catch (IllegalArgumentException ignored) {
    }

    // Bound inside min/max size for disk cache.
    return Math.max(Math.min(size, MAX_DISK_CACHE_SIZE), MIN_DISK_CACHE_SIZE);
  }

  static int calculateMemoryCacheSize(Context context) {
    ActivityManager am = ContextCompat.getSystemService(context, ActivityManager.class);
    boolean largeHeap = (context.getApplicationInfo().flags & FLAG_LARGE_HEAP) != 0;
    int memoryClass = largeHeap ? am.getLargeMemoryClass() : am.getMemoryClass();
    // Target ~15% of the available heap.
    return (int) (1024L * 1024L * memoryClass / 7);
  }

  static boolean isAirplaneModeOn(Context context) {
    ContentResolver contentResolver = context.getContentResolver();
    try {
      if (SDK_INT < JELLY_BEAN_MR1) {
        //noinspection deprecation
        return Settings.System.getInt(contentResolver, Settings.System.AIRPLANE_MODE_ON, 0) != 0;
      }
      return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    } catch (NullPointerException e) {
      // https://github.com/square/picasso/issues/761, some devices might crash here, assume that
      // airplane mode is off.
      return false;
    } catch (SecurityException e) {
      //https://github.com/square/picasso/issues/1197
      return false;
    }
  }

  static boolean hasPermission(Context context, String permission) {
    return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
  }

  static boolean isWebPFile(BufferedSource source) throws IOException {
    return source.rangeEquals(0, WEBP_FILE_HEADER_RIFF)
        && source.rangeEquals(8, WEBP_FILE_HEADER_WEBP);
  }

  static int getResourceId(Resources resources, Request data) throws FileNotFoundException {
    if (data.resourceId != 0 || data.uri == null) {
      return data.resourceId;
    }

    String pkg = data.uri.getAuthority();
    if (pkg == null) throw new FileNotFoundException("No package provided: " + data.uri);

    int id;
    List<String> segments = data.uri.getPathSegments();
    if (segments == null || segments.isEmpty()) {
      throw new FileNotFoundException("No path segments: " + data.uri);
    } else if (segments.size() == 1) {
      try {
        id = Integer.parseInt(segments.get(0));
      } catch (NumberFormatException e) {
        throw new FileNotFoundException("Last path segment is not a resource ID: " + data.uri);
      }
    } else if (segments.size() == 2) {
      String type = segments.get(0);
      String name = segments.get(1);

      id = resources.getIdentifier(name, type, pkg);
    } else {
      throw new FileNotFoundException("More than two path segments: " + data.uri);
    }
    return id;
  }

  static Resources getResources(Context context, Request data) throws FileNotFoundException {
    if (data.resourceId != 0 || data.uri == null) {
      return context.getResources();
    }

    String pkg = data.uri.getAuthority();
    if (pkg == null) throw new FileNotFoundException("No package provided: " + data.uri);
    try {
      PackageManager pm = context.getPackageManager();
      return pm.getResourcesForApplication(pkg);
    } catch (PackageManager.NameNotFoundException e) {
      throw new FileNotFoundException("Unable to obtain resources for package: " + data.uri);
    }
  }

  /**
   * Prior to Android 5, HandlerThread always keeps a stack local reference to the last message
   * that was sent to it. This method makes sure that stack local reference never stays there
   * for too long by sending new messages to it every second.
   */
  static void flushStackLocalLeaks(Looper looper) {
    Handler handler = new Handler(looper) {
      @Override public void handleMessage(Message msg) {
        sendMessageDelayed(obtainMessage(), THREAD_LEAK_CLEANING_MS);
      }
    };
    handler.sendMessageDelayed(handler.obtainMessage(), THREAD_LEAK_CLEANING_MS);
  }

  static class PicassoThreadFactory implements ThreadFactory {
    @Override public Thread newThread(@NonNull Runnable r) {
      return new PicassoThread(r);
    }
  }

  private static class PicassoThread extends Thread {
    PicassoThread(Runnable r) {
      super(r);
    }

    @Override public void run() {
      Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND);
      super.run();
    }
  }
}
