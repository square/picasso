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
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Request.LoadedFrom;
import static com.squareup.picasso.Request.LoadedFrom.DISK;
import static com.squareup.picasso.Request.LoadedFrom.NETWORK;

class NetworkBitmapHunter extends BitmapHunter {
  private final Downloader downloader;
  private LoadedFrom loadedFrom;

  public NetworkBitmapHunter(Picasso picasso, Dispatcher dispatcher, Request request,
      Downloader downloader) {
    super(picasso, dispatcher, request);
    this.downloader = downloader;
  }

  @Override Bitmap decode(Uri uri, PicassoBitmapOptions options) throws IOException {
    InputStream is = null;
    try {
      is = getInputStream();
      return decodeStream(is, options);
    } finally {
      if (is != null) {
        Utils.closeQuietly(is);
      }
    }
  }

  @Override LoadedFrom getLoadedFrom() {
    return loadedFrom;
  }

  private InputStream getInputStream() throws IOException {
    Response response = downloader.load(uri, false);
    loadedFrom = response.cached ? DISK : NETWORK;
    return response.stream;
  }

  private Bitmap decodeStream(InputStream stream, PicassoBitmapOptions options) throws IOException {
    if (stream == null) {
      return null;
    }
    if (options != null && options.inJustDecodeBounds) {
      MarkableInputStream markStream = new MarkableInputStream(stream);
      stream = markStream;

      long mark = markStream.savePosition(1024); // Mirrors BitmapFactory.cpp value.
      BitmapFactory.decodeStream(stream, null, options);
      calculateInSampleSize(options);

      markStream.reset(mark);
    }
    return BitmapFactory.decodeStream(stream, null, options);
  }
}
