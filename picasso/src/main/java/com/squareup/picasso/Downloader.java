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
   * @return {@link InputStream} and {@code boolean} indicating whether or not the image is being
   *         loaded from a local disk cache. <strong>Must not be {@code null}.</strong>
   * @throws IOException if the requested URL cannot successfully be loaded.
   */
  Response load(Uri uri, boolean localCacheOnly) throws IOException;

  /** Response stream and info. */
  class Response {
    final InputStream stream;
    final boolean cached;

    /**
     * Response stream and info.
     *
     * @param stream Image data stream.
     * @param loadedFromCache {@code true} if the source of the stream is from a local disk cache.
     */
    public Response(InputStream stream, boolean loadedFromCache) {
      this.stream = stream;
      this.cached = loadedFromCache;
    }

    @SuppressWarnings("UnusedDeclaration") public InputStream getInputStream() {
      return stream;
    }
  }
}
