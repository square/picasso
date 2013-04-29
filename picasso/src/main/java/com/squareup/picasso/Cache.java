package com.squareup.picasso;

import android.graphics.Bitmap;

/**
 * A memory cache for storing the most recently used images.
 * <p/>
 * <em>Note:</em> The {@link #get(String)} method will be invoked on the main thread.
 */
public interface Cache {
  /** Retrieve an image for the specified {@code key} or {@code null}. */
  Bitmap get(String key);

  /** Store an image in the cache for the specified {@code key}. */
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
