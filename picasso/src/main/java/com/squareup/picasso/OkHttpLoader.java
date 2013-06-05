package com.squareup.picasso;

import android.content.Context;
import android.net.Uri;
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

  private final OkHttpClient client;

  /**
   * Create new loader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   */
  public OkHttpLoader(final Context context) {
    this(Utils.createDefaultCacheDir(context));
  }

  /**
   * Create new loader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   */
  public OkHttpLoader(final File cacheDir) {
    this(cacheDir, Utils.calculateDiskCacheSize(cacheDir));
  }

  /**
   * Create new loader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param maxSize The size limit for the cache.
   */
  public OkHttpLoader(final Context context, final int maxSize) {
    this(Utils.createDefaultCacheDir(context), maxSize);
  }

  /**
   * Create new loader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   * @param maxSize The size limit for the cache.
   */
  public OkHttpLoader(final File cacheDir, final int maxSize) {
    this(new OkHttpClient());
    try {
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

  @Override public Response load(Uri uri, boolean localCacheOnly) throws IOException {
    HttpURLConnection connection = client.open(new URL(uri.toString()));
    connection.setUseCaches(true);
    if (localCacheOnly) {
      connection.setRequestProperty("Cache-Control", "only-if-cached");
    }

    boolean fromCache = parseResponseSourceHeader(connection.getHeaderField(RESPONSE_SOURCE));

    return new Response(connection.getInputStream(), fromCache);
  }
}
