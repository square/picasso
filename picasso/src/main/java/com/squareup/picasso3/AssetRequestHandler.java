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

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import java.io.IOException;
import okio.Okio;
import okio.Source;

import static android.content.ContentResolver.SCHEME_FILE;
import static com.squareup.picasso3.BitmapUtils.decodeStream;
import static com.squareup.picasso3.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso3.Utils.checkNotNull;

class AssetRequestHandler extends RequestHandler {
  private static final String ANDROID_ASSET = "android_asset";
  private static final int ASSET_PREFIX_LENGTH =
      (SCHEME_FILE + ":///" + ANDROID_ASSET + "/").length();

  private final Context context;
  private final Object lock = new Object();
  private volatile AssetManager assetManager;

  AssetRequestHandler(Context context) {
    this.context = context;
  }

  @Override public boolean canHandleRequest(@NonNull Request data) {
    Uri uri = data.uri;
    return uri != null
        && SCHEME_FILE.equals(uri.getScheme())
        && !uri.getPathSegments().isEmpty()
        && ANDROID_ASSET.equals(uri.getPathSegments().get(0));
  }

  @Override
  public void load(@NonNull Picasso picasso, @NonNull Request request, @NonNull Callback callback) {
    initializeIfFirstTime();

    boolean signaledCallback = false;
    try {
      Source source = Okio.source(assetManager.open(getFilePath(request)));
      try {
        Bitmap bitmap = decodeStream(source, request);
        signaledCallback = true;
        callback.onSuccess(new Result(bitmap, DISK));
      } finally {
        try {
          source.close();
        } catch (IOException ignored) {
        }
      }
    } catch (Exception e) {
      if (!signaledCallback) {
        callback.onError(e);
      }
    }
  }

  @Initializer
  private void initializeIfFirstTime() {
    if (assetManager == null) {
      synchronized (lock) {
        if (assetManager == null) {
          assetManager = context.getAssets();
        }
      }
    }
  }

  static String getFilePath(Request request) {
    Uri uri = checkNotNull(request.uri, "request.uri == null");
    return uri.toString().substring(ASSET_PREFIX_LENGTH);
  }
}
