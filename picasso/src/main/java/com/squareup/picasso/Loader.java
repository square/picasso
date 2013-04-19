package com.squareup.picasso;

import java.io.IOException;
import java.io.InputStream;

public interface Loader {
  Response load(String path, boolean allowExpired) throws IOException;

  class Response {
    final InputStream stream;
    final boolean cached;

    public Response(InputStream stream, boolean cached) {
      this.stream = stream;
      this.cached = cached;
    }
  }
}
