package com.squareup.picasso;

import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

/** A mechanism to load images from external resources such as a disk cache and/or the internet. */
public interface Loader {
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
  }
}
