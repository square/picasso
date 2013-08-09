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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;

class NetworkBitmapHunter extends BitmapHunter {
  private final Downloader downloader;
  private final boolean airplaneMode;
  private Picasso.LoadedFrom loadedFrom;

  public NetworkBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Action action,
      Downloader downloader, boolean airplaneMode) {
    super(picasso, dispatcher, cache, action);
    this.downloader = downloader;
    this.airplaneMode = airplaneMode;
  }

  @Override Bitmap decode(Request data, int retryCount)
      throws IOException {
    boolean loadFromLocalCacheOnly = retryCount == 0 || airplaneMode;
    Response response = downloader.load(data.uri, loadFromLocalCacheOnly);
    loadedFrom = response.cached ? DISK : NETWORK;

    Bitmap result = response.getBitmap();
    if (result != null) {
      return result;
    }

    InputStream is = null;
    try {
      is = response.getInputStream();
      return decodeStream(is, data);
    } finally {
      Utils.closeQuietly(is);
    }
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return loadedFrom;
  }

  private Bitmap decodeStream(InputStream stream, Request data) throws IOException {
    if (stream == null) {
      return null;
    }
    BitmapFactory.Options options = null;
    if (data.hasSize()) {
      options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;

      MarkableInputStream markStream = new MarkableInputStream(stream);
      stream = markStream;

      long mark = markStream.savePosition(1024); // Mirrors BitmapFactory.cpp value.
      BitmapFactory.decodeStream(stream, null, options);
      calculateInSampleSize(data.targetWidth, data.targetHeight, options);

      markStream.reset(mark);
    }
    return BitmapFactory.decodeStream(stream, null, options);
  }
}
