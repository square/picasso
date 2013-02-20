package com.squareup.picasso;

import android.content.Context;
import android.net.http.HttpResponseCache;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class DefaultHttpLoader implements Loader {

  public DefaultHttpLoader(Context context) {
    if (HttpResponseCache.getInstalled() == null) {
      try {
        HttpResponseCache.install(new File(context.getCacheDir(), "picasso-cache"),
            10 * 1024 * 1024);
      } catch (IOException ignored) {
      }
    }
  }

  @Override public Response load(String path, boolean allowExpired) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
    connection.setUseCaches(true);
    if (allowExpired) {
      connection.setRequestProperty("Cache-Control", "only-if-cached");
    }

    // TODO Should handle this.
    boolean fromCache = false;
    return new Response(connection.getInputStream(), fromCache, allowExpired);
  }
}