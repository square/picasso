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

import android.content.Context;
import android.net.Uri;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.squareup.picasso.Utils.parseResponseSourceHeader;

/** A {@link Downloader} which uses OkHttp to download images. */
public class OkHttpDownloader implements Downloader {
  static final String RESPONSE_SOURCE_ANDROID = "X-Android-Response-Source";
  static final String RESPONSE_SOURCE_OKHTTP = "OkHttp-Response-Source";

  private final OkUrlFactory urlFactory;

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   */
  public OkHttpDownloader(final Context context) {
    this(Utils.createDefaultCacheDir(context));
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   */
  public OkHttpDownloader(final File cacheDir) {
    this(cacheDir, Utils.calculateDiskCacheSize(cacheDir));
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param maxSize The size limit for the cache.
   */
  public OkHttpDownloader(final Context context, final long maxSize) {
    this(Utils.createDefaultCacheDir(context), maxSize);
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   * @param maxSize The size limit for the cache.
   */
  public OkHttpDownloader(final File cacheDir, final long maxSize) {
    this(new OkHttpClient());
    try {
      urlFactory.client().setCache(new com.squareup.okhttp.Cache(cacheDir, maxSize));
    } catch (IOException ignored) {
    }
  }

  /**
   * Create a new downloader that uses the specified OkHttp instance. A response cache will not be
   * automatically configured.
   */
  public OkHttpDownloader(OkHttpClient client) {
    this.urlFactory = new OkUrlFactory(client);
  }

  protected HttpURLConnection openConnection(Uri uri) throws IOException {
    HttpURLConnection connection = urlFactory.open(new URL(uri.toString()));
    connection.setConnectTimeout(Utils.DEFAULT_CONNECT_TIMEOUT);
    connection.setReadTimeout(Utils.DEFAULT_READ_TIMEOUT);
    return connection;
  }

  protected OkHttpClient getClient() {
    return urlFactory.client();
  }

  @Override public Response load(Uri uri, boolean localCacheOnly) throws IOException {
    HttpURLConnection connection = openConnection(uri);
    connection.setUseCaches(true);
    if (localCacheOnly) {
      connection.setRequestProperty("Cache-Control", "only-if-cached,max-age=" + Integer.MAX_VALUE);
    }

    int responseCode = connection.getResponseCode();
    if (responseCode >= 300) {
      connection.disconnect();
      throw new ResponseException(responseCode + " " + connection.getResponseMessage());
    }

    String responseSource = connection.getHeaderField(RESPONSE_SOURCE_OKHTTP);
    if (responseSource == null) {
      responseSource = connection.getHeaderField(RESPONSE_SOURCE_ANDROID);
    }

    long contentLength = connection.getHeaderFieldInt("Content-Length", -1);
    boolean fromCache = parseResponseSourceHeader(responseSource);

    return new Response(connection.getInputStream(), fromCache, contentLength);
  }

  @Override public void shutdown() {
    com.squareup.okhttp.Cache cache = urlFactory.client().getCache();
    if (cache != null) {
      try {
        cache.close();
      } catch (IOException ignored) {
      }
    }
  }
}
