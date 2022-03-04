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
package com.squareup.picasso3

import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.BitmapCompat

/** A memory cache which uses a least-recently used eviction policy.  */
internal class PlatformLruCache(maxByteCount: Int) {

  /** Create a cache with a given maximum size in bytes.  */
  val cache =
    object : LruCache<String, BitmapAndSize>(if (maxByteCount != 0) maxByteCount else 1) {
      override fun sizeOf(
        key: String,
        value: BitmapAndSize
      ): Int = value.byteCount
    }

  operator fun get(key: String): Bitmap? = cache[key]?.bitmap

  operator fun set(
    key: String,
    bitmap: Bitmap
  ) {
    val byteCount = BitmapCompat.getAllocationByteCount(bitmap)
    // If the bitmap is too big for the cache, don't even attempt to store it. Doing so will cause
    // the cache to be cleared. Instead just evict an existing element with the same key if it
    // exists.
    if (byteCount > maxSize()) {
      cache.remove(key)
      return
    }

    cache.put(key, BitmapAndSize(bitmap, byteCount))
  }

  fun size(): Int = cache.size()

  fun maxSize(): Int = cache.maxSize()

  fun clear() = cache.evictAll()

  fun clearKeyUri(uri: String) {
    // Keys are prefixed with a URI followed by '\n'.
    for (key in cache.snapshot().keys) {
      if (key.startsWith(uri) &&
        key.length > uri.length &&
        key[uri.length] == Request.KEY_SEPARATOR
      ) {
        cache.remove(key)
      }
    }
  }

  /** Returns the number of times [get] returned a value.  */
  fun hitCount(): Int = cache.hitCount()

  /** Returns the number of times [get] returned `null`.  */
  fun missCount(): Int = cache.missCount()

  /** Returns the number of times [set] was called.  */
  fun putCount(): Int = cache.putCount()

  /** Returns the number of values that have been evicted.  */
  fun evictionCount(): Int = cache.evictionCount()

  internal class BitmapAndSize(
    val bitmap: Bitmap,
    val byteCount: Int
  )
}
