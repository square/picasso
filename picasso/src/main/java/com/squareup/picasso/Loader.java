package com.squareup.picasso;

import java.io.IOException;
import java.io.InputStream;

public interface Loader {
  /**
   * Download the specified image {@code url} from the internet.
   *
   * @param url Remote image URL.
   * @param localCacheOnly If {@code true} the URL should only be loaded if available in a local
   * disk cache.
   * @return {@link InputStream} and {@code boolean} indicating whether or not the image is being
   *         loaded from a local disk cache. <strong>Must not be {@code null}.</strong>
   * @throws IOException if the requested URL cannot successfully be loaded.
   */
  Response load(String url, boolean localCacheOnly) throws IOException;

  class Response {
    final InputStream stream;
    final boolean cached;

    public Response(InputStream stream, boolean cached) {
      this.stream = stream;
      this.cached = cached;
    }
  }
}
