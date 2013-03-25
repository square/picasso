package com.squareup.picasso;

import android.content.Context;
import android.net.http.HttpResponseCache;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class DefaultHttpLoader implements Loader {

  private static final String PICASSO_CACHE = "picasso-cache";
  private static final int MAX_SIZE = 10 * 1024 * 1024;
  private static final int FLUSH_DELAY = 350;

  private static final Object lock = new Object();
  private static volatile HttpResponseCache cache;

  private final Context context;

  private final FlushCacheRunnable flushCacheRunnable;
  private final HandlerThread purgeThread;
  private Handler purgeThreadHandler;

  public DefaultHttpLoader(Context context) {
    this.context = context.getApplicationContext();
    this.flushCacheRunnable = new FlushCacheRunnable();
    this.purgeThread =
        new HandlerThread("loader-purge-thread", Process.THREAD_PRIORITY_BACKGROUND) {
          @Override protected void onLooperPrepared() {
            purgeThreadHandler = new Handler(purgeThread.getLooper());
          }
        };
    this.purgeThread.start();
  }

  @Override public Response load(String path, boolean allowExpired) throws IOException {
    if (purgeThreadHandler != null) {
      purgeThreadHandler.removeCallbacks(flushCacheRunnable);
    }

    installCacheIfNeeded(context);

    HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
    connection.setUseCaches(true);
    if (allowExpired) {
      connection.setRequestProperty("Cache-Control", "only-if-cached");
    }

    // TODO Should handle this.
    boolean fromCache = false;

    if (purgeThreadHandler != null) {
      purgeThreadHandler.postDelayed(flushCacheRunnable, FLUSH_DELAY);
    }
    return new Response(connection.getInputStream(), fromCache, allowExpired);
  }

  private static void installCacheIfNeeded(Context context) {
    // DCL + volatile should be safe after Java 5.
    if (cache == null) {
      try {
        synchronized (lock) {
          if (cache == null) {
            cache =
                HttpResponseCache.install(new File(context.getCacheDir(), PICASSO_CACHE), MAX_SIZE);
          }
        }
      } catch (IOException ignored) {
      }
    }
  }

  private static final class FlushCacheRunnable implements Runnable {
    @Override public void run() {
      if (cache != null) {
        cache.flush();
      }
    }
  }
}
