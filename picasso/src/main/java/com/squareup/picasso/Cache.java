package com.squareup.picasso;

import android.graphics.Bitmap;
import java.io.IOException;

public interface Cache {
  Bitmap get(String key) throws IOException;

  void set(String key, Bitmap bitmap) throws IOException;
}
