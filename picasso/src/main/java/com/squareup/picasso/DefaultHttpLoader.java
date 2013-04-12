package com.squareup.picasso;

import android.content.Context;
import android.net.http.HttpResponseCache;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.squareup.picasso.Utils.parseResponseSourceHeader;

public class DefaultHttpLoader implements Loader {
  private static final String RESPONSE_SOURCE = "X-Android-Response-Source";
  private static final String PICASSO_CACHE = "picasso-cache";
  private static final int MAX_SIZE = 10 * 1024 * 1024;

  private static final Object lock = new Object();
  private static volatile HttpResponseCache cache;

  private final Context context;

  public DefaultHttpLoader(Context context) {
    this.context = context.getApplicationContext();
  }

  protected HttpURLConnection openConnection(String path) throws IOException {
    return (HttpURLConnection) new URL(path).openConnection();
  }

  @Override public Response load(String path, boolean allowExpired) throws IOException {
    installCacheIfNeeded(context);

    HttpURLConnection connection = openConnection(path);
    connection.setUseCaches(true);
    if (allowExpired) {
      connection.setRequestProperty("Cache-Control", "only-if-cached");
    }

    boolean fromCache = parseResponseSourceHeader(connection.getHeaderField(RESPONSE_SOURCE));

    return new Response(connection.getInputStream(), fromCache, allowExpired);
  }

  private static void installCacheIfNeeded(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
      return;
    }
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
}
