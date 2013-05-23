package com.squareup.picasso;

import android.content.Context;
import com.squareup.okhttp.HttpResponseCache;
import com.squareup.okhttp.OkHttpClient;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.squareup.picasso.Utils.parseResponseSourceHeader;

/** A {@link Loader} which uses OkHttp to download images. */
public class OkHttpLoader implements Loader {
  static final String RESPONSE_SOURCE = "X-Android-Response-Source";
  private static final String PICASSO_CACHE = "picasso-cache";

  private final OkHttpClient client;

  /**
   * Create new loader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   */
  public OkHttpLoader(Context context) {
    this(new OkHttpClient());
    try {
      File cacheDir = new File(context.getApplicationContext().getCacheDir(), PICASSO_CACHE);
      int maxSize = Utils.calculateDiskCacheSize(cacheDir);
      client.setResponseCache(new HttpResponseCache(cacheDir, maxSize));
    } catch (IOException ignored) {
    }
  }

  /**
   * Create a new loader that uses the specified OkHttp instance. A response cache will not be
   * automatically configured.
   */
  public OkHttpLoader(OkHttpClient client) {
    this.client = client;
  }

  @Override public Response load(String url, boolean localCacheOnly) throws IOException {
    HttpURLConnection connection = client.open(new URL(url));
    connection.setUseCaches(true);
    if (localCacheOnly) {
      connection.setRequestProperty("Cache-Control", "only-if-cached");
    }

    boolean fromCache = parseResponseSourceHeader(connection.getHeaderField(RESPONSE_SOURCE));

    return new Response(connection.getInputStream(), fromCache);
  }
}
