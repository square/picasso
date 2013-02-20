package com.squareup.picasso;

import android.graphics.Bitmap;
import java.io.IOException;

public interface DiskCache {

  Bitmap get(String key) throws IOException;

  void set(String key, Bitmap bitmap) throws IOException;
}
