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
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

import static android.content.ContentResolver.SCHEME_FILE;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class AssetRequestHandler extends RequestHandler {
  protected static final String ANDROID_ASSET = "android_asset";
  private static final int ASSET_PREFIX_LENGTH =
      (SCHEME_FILE + ":///" + ANDROID_ASSET + "/").length();

  private final AssetManager assetManager;
  private final Context context;

  public AssetRequestHandler(Context context) {
    this.context = context.getApplicationContext();
    assetManager = context.getAssets();
  }

  @Override public boolean canHandleRequest(Request data) {
    Uri uri = data.uri;
    return (SCHEME_FILE.equals(uri.getScheme())
        && !uri.getPathSegments().isEmpty() && ANDROID_ASSET.equals(uri.getPathSegments().get(0)));
  }

  @Override public Result load(Request data) throws IOException {
    String filePath = data.uri.toString().substring(ASSET_PREFIX_LENGTH);
    return new Result(decodeAsset(data, filePath), DISK);
  }

  Bitmap decodeAsset(Request data, String filePath) throws IOException {
    final BitmapFactory.Options options = createBitmapOptions(data, context);
    if (requiresInSampleSize(options)) {
      InputStream is = null;
      try {
        is = assetManager.open(filePath);
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(data.targetWidth, data.targetHeight, options, data);
    }
    InputStream is = assetManager.open(filePath);
    try {
      return BitmapFactory.decodeStream(is, null, options);
    } finally {
      Utils.closeQuietly(is);
    }
  }
}
