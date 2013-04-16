package com.squareup.picasso;

import android.graphics.Bitmap;

public interface Cache {
  Bitmap get(String key);

  void set(String key, Bitmap bitmap);

  /** A cache which does not store any values. */
  Cache NONE = new Cache() {
    @Override public Bitmap get(String key) {
      return null;
    }

    @Override public void set(String key, Bitmap bitmap) {
      // Ignore.
    }
  };
}
