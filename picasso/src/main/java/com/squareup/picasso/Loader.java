package com.squareup.picasso;

import java.io.IOException;
import java.io.InputStream;

public interface Loader {
  InputStream load(String path) throws IOException;
}
