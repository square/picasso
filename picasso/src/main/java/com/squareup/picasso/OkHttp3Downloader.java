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
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

/** A {@link Downloader} which uses OkHttp to download images. */
public final class OkHttp3Downloader implements Downloader {
  private final Call.Factory client;
  private final Cache cache;
  private boolean sharedClient = true;

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   */
  public OkHttp3Downloader(final Context context) {
    this(Utils.createDefaultCacheDir(context));
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into the specified
   * directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   */
  public OkHttp3Downloader(final File cacheDir) {
    this(cacheDir, Utils.calculateDiskCacheSize(cacheDir));
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param maxSize The size limit for the cache.
   */
  public OkHttp3Downloader(final Context context, final long maxSize) {
    this(Utils.createDefaultCacheDir(context), maxSize);
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into the specified
   * directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   * @param maxSize The size limit for the cache.
   */
  public OkHttp3Downloader(final File cacheDir, final long maxSize) {
    this(new OkHttpClient.Builder().cache(new Cache(cacheDir, maxSize)).build());
    sharedClient = false;
  }

  /**
   * Create a new downloader that uses the specified OkHttp instance. A response cache will not be
   * automatically configured.
   */
  public OkHttp3Downloader(OkHttpClient client) {
    this.client = client;
    this.cache = client.cache();
  }

  /** Create a new downloader that uses the specified {@link Call.Factory} instance. */
  public OkHttp3Downloader(Call.Factory client) {
    this.client = client;
    this.cache = null;
  }

  @VisibleForTesting Cache getCache() {
    return ((OkHttpClient) client).cache();
  }

  @Override public Response load(@NonNull Uri uri, int networkPolicy) throws IOException {
    CacheControl cacheControl = null;
    if (networkPolicy != 0) {
      if (NetworkPolicy.isOfflineOnly(networkPolicy)) {
        cacheControl = CacheControl.FORCE_CACHE;
      } else {
        CacheControl.Builder builder = new CacheControl.Builder();
        if (!NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
          builder.noCache();
        }
        if (!NetworkPolicy.shouldWriteToDiskCache(networkPolicy)) {
          builder.noStore();
        }
        cacheControl = builder.build();
      }
    }

    Request.Builder builder = new okhttp3.Request.Builder().url(uri.toString());
    if (cacheControl != null) {
      builder.cacheControl(cacheControl);
    }

    okhttp3.Response response = client.newCall(builder.build()).execute();
    int responseCode = response.code();
    if (responseCode >= 300) {
      response.body().close();
      throw new ResponseException(responseCode + " " + response.message(), networkPolicy,
          responseCode);
    }

    boolean fromCache = response.cacheResponse() != null;

    ResponseBody responseBody = response.body();
    return new Response(responseBody.byteStream(), fromCache, responseBody.contentLength());
  }

  @Override public void shutdown() {
    if (!sharedClient) {
      if (cache != null) {
        try {
          cache.close();
        } catch (IOException ignored) {
        }
      }
    }
  }
}
