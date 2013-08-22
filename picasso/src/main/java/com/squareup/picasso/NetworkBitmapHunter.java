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
import android.net.NetworkInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;

class NetworkBitmapHunter extends BitmapHunter {
  /* WebP file header
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      'R'      |      'I'      |      'F'      |      'F'      |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                           File Size                           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      'W'      |      'E'      |      'B'      |      'P'      |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   */
  private static final int WEBP_FILE_HEADER_SIZE = 12;
  private static final String WEBP_FILE_HEADER_RIFF = "RIFF";
  private static final String WEBP_FILE_HEADER_WEBP = "WEBP";

  static final int DEFAULT_RETRY_COUNT = 2;
  private final Downloader downloader;

  private Picasso.LoadedFrom loadedFrom;
  int retryCount;

  public NetworkBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
      Action action, Downloader downloader) {
    super(picasso, dispatcher, cache, stats, action);
    this.downloader = downloader;
    this.retryCount = DEFAULT_RETRY_COUNT;
  }

  @Override Bitmap decode(Request data)
      throws IOException {
    boolean loadFromLocalCacheOnly = retryCount == 0;
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

  @Override boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
    boolean hasRetries = retryCount > 0;
    if (!hasRetries) {
      return false;
    }
    retryCount--;
    return info == null || info.isConnectedOrConnecting();
  }

  private Bitmap decodeStream(InputStream stream, Request data) throws IOException {
    if (stream == null) {
      return null;
    }
    MarkableInputStream markStream = new MarkableInputStream(stream);
    stream = markStream;

    long mark = markStream.savePosition(1024); // Mirrors BitmapFactory.cpp value.

    byte[] fileHeaderBytes = new byte[WEBP_FILE_HEADER_SIZE];
    boolean isWebPFile = false;
    if (stream.read(fileHeaderBytes, 0, WEBP_FILE_HEADER_SIZE) == WEBP_FILE_HEADER_SIZE) {
      //if a file's header starts with RIFF and end with WEBP, the file is a WebP file
      isWebPFile = WEBP_FILE_HEADER_RIFF.equals(new String(fileHeaderBytes, 0, 4))
        && WEBP_FILE_HEADER_WEBP.equals(new String(fileHeaderBytes, 8, 4));
    }
    markStream.reset(mark);
    /*When decode WebP network stream, BitmapFactory throw JNI Exception and make app crash.
      Decode byte array instead
      */
    if (isWebPFile) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024 * 4];
      int n = 0;
      while (-1 != (n = stream.read(buffer))) {
        byteArrayOutputStream.write(buffer, 0, n);
      }
      byte[] bytes = byteArrayOutputStream.toByteArray();
      BitmapFactory.Options options = new BitmapFactory.Options();

      //i'm not sure about these
      options.inPurgeable = true;
      options.inInputShareable = true;

      if (data.hasSize()) {
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        calculateInSampleSize(data.targetWidth, data.targetHeight, options);
      }
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    } else {
      BitmapFactory.Options options = null;
      if (data.hasSize()) {
        options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeStream(stream, null, options);
        calculateInSampleSize(data.targetWidth, data.targetHeight, options);

        markStream.reset(mark);
      }
      return BitmapFactory.decodeStream(stream, null, options);
    }
  }
}
