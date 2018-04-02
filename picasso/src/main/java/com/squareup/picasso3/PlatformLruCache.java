/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.BitmapCompat;
import android.util.LruCache;

import static com.squareup.picasso3.Request.KEY_SEPARATOR;

/** A memory cache which uses a least-recently used eviction policy. */
final class PlatformLruCache {
  final LruCache<String, BitmapAndSize> cache;

  /** Create a cache with a given maximum size in bytes. */
  PlatformLruCache(int maxByteCount) {
    cache = new LruCache<String, BitmapAndSize>(maxByteCount != 0 ? maxByteCount : 1) {
      @Override protected int sizeOf(String key, BitmapAndSize value) {
        return value.byteCount;
      }
    };
  }

  @Nullable public Bitmap get(@NonNull String key) {
    BitmapAndSize bitmapAndSize = cache.get(key);
    return bitmapAndSize != null ? bitmapAndSize.bitmap : null;
  }

  void set(@NonNull String key, @NonNull Bitmap bitmap) {
    if (key == null || bitmap == null) {
      throw new NullPointerException("key == null || bitmap == null");
    }

    int byteCount = BitmapCompat.getAllocationByteCount(bitmap);

    // If the bitmap is too big for the cache, don't even attempt to store it. Doing so will cause
    // the cache to be cleared. Instead just evict an existing element with the same key if it
    // exists.
    if (byteCount > maxSize()) {
      cache.remove(key);
      return;
    }

    cache.put(key, new BitmapAndSize(bitmap, byteCount));
  }

  int size() {
    return cache.size();
  }

  int maxSize() {
    return cache.maxSize();
  }

  void clear() {
    cache.evictAll();
  }

  void clearKeyUri(String uri) {
    // Keys are prefixed with a URI followed by '\n'.
    for (String key : cache.snapshot().keySet()) {
      if (key.startsWith(uri)
          && key.length() > uri.length()
          && key.charAt(uri.length()) == KEY_SEPARATOR) {
        cache.remove(key);
      }
    }
  }

  /** Returns the number of times {@link #get} returned a value. */
  int hitCount() {
    return cache.hitCount();
  }

  /** Returns the number of times {@link #get} returned {@code null}. */
  int missCount() {
    return cache.missCount();
  }

  /** Returns the number of times {@link #set(String, Bitmap)} was called. */
  int putCount() {
    return cache.putCount();
  }

  /** Returns the number of values that have been evicted. */
  int evictionCount() {
    return cache.evictionCount();
  }

  static final class BitmapAndSize {
    final Bitmap bitmap;
    final int byteCount;

    BitmapAndSize(Bitmap bitmap, int byteCount) {
      this.bitmap = bitmap;
      this.byteCount = byteCount;
    }
  }
}
