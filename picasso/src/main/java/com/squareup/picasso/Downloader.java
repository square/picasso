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
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

/** A mechanism to load images from external resources such as a disk cache and/or the internet. */
public interface Downloader {
  /**
   * Download the specified image {@code url} from the internet.
   *
   * @param uri Remote image URL.
   * @param localCacheOnly If {@code true} the URL should only be loaded if available in a local
   * disk cache.
   * @return {@link Response} containing either a {@link Bitmap} representation of the request or an
   * {@link InputStream} for the image data. {@code null} can be returned to indicate a problem
   * loading the bitmap.
   * @throws IOException if the requested URL cannot successfully be loaded.
   */
  Response load(Uri uri, boolean localCacheOnly) throws IOException;

  /** Thrown for non-2XX responses. */
  class ResponseException extends IOException {
    public ResponseException(String message) {
      super(message);
    }
  }

  /** Response stream or bitmap and info. */
  class Response {
    final InputStream stream;
    final Bitmap bitmap;
    final boolean cached;
    final long contentLength;

    /**
     * Response image and info.
     *
     * @param bitmap Image.
     * @param loadedFromCache {@code true} if the source of the image is from a local disk cache.
     * @deprecated Use {@link Response#Response(android.graphics.Bitmap, boolean, long)} instead.
     */
    @Deprecated @SuppressWarnings("UnusedDeclaration")
    public Response(Bitmap bitmap, boolean loadedFromCache) {
      this(bitmap, loadedFromCache, -1);
    }

    /**
     * Response stream and info.
     *
     * @param stream Image data stream.
     * @param loadedFromCache {@code true} if the source of the stream is from a local disk cache.
     * @deprecated Use {@link Response#Response(java.io.InputStream, boolean, long)} instead.
     */
    @Deprecated @SuppressWarnings("UnusedDeclaration")
    public Response(InputStream stream, boolean loadedFromCache) {
      this(stream, loadedFromCache, -1);
    }

    /**
     * Response image and info.
     *
     * @param bitmap Image.
     * @param loadedFromCache {@code true} if the source of the image is from a local disk cache.
     * @param contentLength The content length of the response, typically derived by the
     * {@code Content-Length} HTTP header.
     */
    public Response(Bitmap bitmap, boolean loadedFromCache, long contentLength) {
      if (bitmap == null) {
        throw new IllegalArgumentException("Bitmap may not be null.");
      }
      this.stream = null;
      this.bitmap = bitmap;
      this.cached = loadedFromCache;
      this.contentLength = contentLength;
    }

    /**
     * Response stream and info.
     *
     * @param stream Image data stream.
     * @param loadedFromCache {@code true} if the source of the stream is from a local disk cache.
     * @param contentLength The content length of the response, typically derived by the
     * {@code Content-Length} HTTP header.
     */
    public Response(InputStream stream, boolean loadedFromCache, long contentLength) {
      if (stream == null) {
        throw new IllegalArgumentException("Stream may not be null.");
      }
      this.stream = stream;
      this.bitmap = null;
      this.cached = loadedFromCache;
      this.contentLength = contentLength;
    }

    /**
     * Input stream containing image data.
     * <p>
     * If this returns {@code null}, image data will be available via {@link #getBitmap()}.
     */
    public InputStream getInputStream() {
      return stream;
    }

    /**
     * Bitmap representing the image.
     * <p>
     * If this returns {@code null}, image data will be available via {@link #getInputStream()}.
     */
    public Bitmap getBitmap() {
      return bitmap;
    }

    /** Content length of the response. */
    public long getContentLength() {
      return contentLength;
    }
  }
}
