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

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import java.io.IOException;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

final class OkHttp3Downloader {
  @VisibleForTesting final Call.Factory client;
  private final @Nullable Cache cache;
  private final boolean sharedClient;

  OkHttp3Downloader(Call.Factory client, @Nullable Cache cache, boolean sharedClient) {
    this.client = client;
    this.cache = cache;
    this.sharedClient = sharedClient;
  }

  Response load(Request request) throws IOException {
    return client.newCall(request).execute();
  }

  void shutdown() {
    if (!sharedClient && cache != null) {
      try {
        cache.close();
      } catch (IOException ignored) {
      }
    }
  }
}
