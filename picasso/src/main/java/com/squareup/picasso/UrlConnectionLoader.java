package com.squareup.picasso;

import android.content.Context;
import android.net.Uri;
import android.net.http.HttpResponseCache;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.squareup.picasso.Utils.parseResponseSourceHeader;

/**
 * A {@link Loader} which uses {@link HttpURLConnection} to download images. A disk cache of 10MB
 * will automatically be installed in the application's cache directory, when available.
 */
public class UrlConnectionLoader implements Loader {
  static final String RESPONSE_SOURCE = "X-Android-Response-Source";

  private static final Object lock = new Object();
  static volatile Object cache;

  private final Context context;

  public UrlConnectionLoader(Context context) {
    this.context = context.getApplicationContext();
  }

  protected HttpURLConnection openConnection(String path) throws IOException {
    return (HttpURLConnection) new URL(path).openConnection();
  }

  @Override public Response load(Uri uri, boolean localCacheOnly) throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      installCacheIfNeeded(context);
    }

    HttpURLConnection connection = openConnection(uri.toString());
    connection.setUseCaches(true);
    if (localCacheOnly) {
      connection.setRequestProperty("Cache-Control", "only-if-cached");
    }

    boolean fromCache = parseResponseSourceHeader(connection.getHeaderField(RESPONSE_SOURCE));

    return new Response(connection.getInputStream(), fromCache);
  }

  private static void installCacheIfNeeded(Context context) {
    // DCL + volatile should be safe after Java 5.
    if (cache == null) {
      try {
        synchronized (lock) {
          if (cache == null) {
            cache = ResponseCacheIcs.install(context);
          }
        }
      } catch (IOException ignored) {
      }
    }
  }

  private static class ResponseCacheIcs {
    static Object install(Context context) throws IOException {
      File cacheDir = Utils.createDefaultCacheDir(context);
      HttpResponseCache cache = HttpResponseCache.getInstalled();
      if (cache == null) {
        int maxSize = Utils.calculateDiskCacheSize(cacheDir);
        cache = HttpResponseCache.install(cacheDir, maxSize);
      }
      return cache;
    }
  }
}
