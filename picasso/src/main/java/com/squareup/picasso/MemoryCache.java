package com.squareup.picasso;

import android.graphics.Bitmap;
import java.io.IOException;

public interface MemoryCache {
  Bitmap get(String key);

  void set(String key, Bitmap bitmap);
}
