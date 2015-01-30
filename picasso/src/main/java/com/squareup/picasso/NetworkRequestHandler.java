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
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;

class NetworkRequestHandler extends RequestHandler {
  static final int RETRY_COUNT = 2;
  private static final int MARKER = 65536;

  private static final String SCHEME_HTTP = "http";
  private static final String SCHEME_HTTPS = "https";

  private final Downloader downloader;
  private final Stats stats;

  public NetworkRequestHandler(Downloader downloader, Stats stats) {
    this.downloader = downloader;
    this.stats = stats;
  }

  @Override public boolean canHandleRequest(Request data) {
    String scheme = data.uri.getScheme();
    return (SCHEME_HTTP.equals(scheme) || SCHEME_HTTPS.equals(scheme));
  }

  @Override public Result load(Request data, int networkPolicy) throws IOException {
    Response response = downloader.load(data.uri, data.networkPolicy);
    if (response == null) {
      return null;
    }

    Picasso.LoadedFrom loadedFrom = response.cached ? DISK : NETWORK;

    Bitmap bitmap = response.getBitmap();
    if (bitmap != null) {
      return new Result(bitmap, loadedFrom);
    }

    InputStream is = response.getInputStream();
    if (is == null) {
      return null;
    }
    // Sometimes response content length is zero when requests are being replayed. Haven't found
    // root cause to this but retrying the request seems safe to do so.
    if (loadedFrom == DISK && response.getContentLength() == 0) {
      Utils.closeQuietly(is);
      throw new ContentLengthException("Received response with 0 content-length header.");
    }
    if (loadedFrom == NETWORK && response.getContentLength() > 0) {
      stats.dispatchDownloadFinished(response.getContentLength());
    }
    try {
      return new Result(decodeStream(is, data), loadedFrom);
    } finally {
      Utils.closeQuietly(is);
    }
  }

  @Override int getRetryCount() {
    return RETRY_COUNT;
  }

  @Override boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
    return info == null || info.isConnected();
  }

  @Override boolean supportsReplay() {
    return true;
  }

  private Bitmap decodeStream(InputStream stream, Request data) throws IOException {
    MarkableInputStream markStream = new MarkableInputStream(stream);
    stream = markStream;

    long mark = markStream.savePosition(MARKER);

    final BitmapFactory.Options options = createBitmapOptions(data);
    final boolean calculateSize = requiresInSampleSize(options);

    boolean isWebPFile = Utils.isWebPFile(stream);
    markStream.reset(mark);
    // When decode WebP network stream, BitmapFactory throw JNI Exception and make app crash.
    // Decode byte array instead
    if (isWebPFile) {
      byte[] bytes = Utils.toByteArray(stream);
      if (calculateSize) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        calculateInSampleSize(data.targetWidth, data.targetHeight, options, data);
      }
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    } else {
      if (calculateSize) {
        BitmapFactory.decodeStream(stream, null, options);
        calculateInSampleSize(data.targetWidth, data.targetHeight, options, data);

        markStream.reset(mark);
      }
      Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
      if (bitmap == null) {
        // Treat null as an IO exception, we will eventually retry.
        throw new IOException("Failed to decode stream.");
      }
      return bitmap;
    }
  }

  static class ContentLengthException extends IOException {
    public ContentLengthException(String message) {
      super(message);
    }
  }
}
