package com.squareup.picasso;

import android.graphics.Bitmap;

public interface Cache {
  /** The value is not in the cache. */
  int NO = 0;
  /** The value is in the cache. */
  int YES = 1;
  /** The value may be in the cache but cannot be queried deterministically. */
  int MAYBE = 2;

  Bitmap get(String key);
  void set(String key, Bitmap bitmap);
  void remove(String key);

  /**
   * Check whether or not a value for {@code key} is present. Will return one of {@link #YES},
   * {@link #NO}, or {@link #MAYBE}.
   */
  int contains(String key);
}
