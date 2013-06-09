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

  /** Returns the current size of the cache in bytes. */
  int size();

  /** Returns the maximum size in bytes that the cache can hold. */
  int maxSize();

  /** Evicts all the items from the cache. */
  void evictAll();

  /** A cache which does not store any values. */
  Cache NONE = new Cache() {
    @Override public Bitmap get(String key) {
      return null;
    }

    @Override public void set(String key, Bitmap bitmap) {
      // Ignore.
    }

    @Override public int size() {
      return 0;
    }

    @Override public int maxSize() {
      return 0;
    }

    @Override public void evictAll() {
    }
  };
}
