/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.squareup.picasso.external;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static library version of {@link android.util.LruCache}. Used to write apps
 * that run on API levels prior to 12. When running on API level 12 or above,
 * this implementation is still used; it does not try to switch to the
 * framework's implementation. See the framework SDK documentation for a class
 * overview.
 */
public class LruCache<K, V> {
  private final LinkedHashMap<K, V> map;

  /** Size of this cache in units. Not necessarily the number of elements. */
  private int size;
  private int maxSize;

  private int putCount;
  private int createCount;
  private int evictionCount;
  private int hitCount;
  private int missCount;

  /**
   * @param maxSize for caches that do not override {@link #sizeOf}, this is
   * the maximum number of entries in the cache. For all other caches,
   * this is the maximum sum of the sizes of the entries in this cache.
   */
  public LruCache(int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize <= 0");
    }
    this.maxSize = maxSize;
    this.map = new LinkedHashMap<K, V>(0, 0.75f, true);
  }

  /**
   * Returns the value for {@code key} if it exists in the cache or can be
   * created by {@code #create}. If a value was returned, it is moved to the
   * head of the queue. This returns null if a value is not cached and cannot
   * be created.
   */
  public final V get(K key) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    V mapValue;
    synchronized (this) {
      mapValue = map.get(key);
      if (mapValue != null) {
        hitCount++;
        return mapValue;
      }
      missCount++;
    }

    // Attempt to create a value. This may take a long time, and the map
    // may be different when create() returns. If a conflicting value was
    // added to the map while create() was working, we leave that value in
    // the map and release the created value.

    V createdValue = create(key);
    if (createdValue == null) {
      return null;
    }

    synchronized (this) {
      createCount++;
      mapValue = map.put(key, createdValue);

      if (mapValue != null) {
        // There was a conflict so undo that last put
        map.put(key, mapValue);
      } else {
        size += safeSizeOf(key, createdValue);
      }
    }

    if (mapValue != null) {
      entryRemoved(false, key, createdValue, mapValue);
      return mapValue;
    } else {
      trimToSize(maxSize);
      return createdValue;
    }
  }

  /**
   * Caches {@code value} for {@code key}. The value is moved to the head of
   * the queue.
   *
   * @return the previous value mapped by {@code key}.
   */
  public final V put(K key, V value) {
    if (key == null || value == null) {
      throw new NullPointerException("key == null || value == null");
    }

    V previous;
    synchronized (this) {
      putCount++;
      size += safeSizeOf(key, value);
      previous = map.put(key, value);
      if (previous != null) {
        size -= safeSizeOf(key, previous);
      }
    }

    if (previous != null) {
      entryRemoved(false, key, previous, value);
    }

    trimToSize(maxSize);
    return previous;
  }

  /**
   * Remove the eldest entries until the total of remaining entries is at or
   * below the requested size.
   *
   * @param maxSize the maximum size of the cache before returning. May be -1
   * to evict even 0-sized elements.
   */
  public void trimToSize(int maxSize) {
    while (true) {
      K key;
      V value;
      synchronized (this) {
        if (size < 0 || (map.isEmpty() && size != 0)) {
          throw new IllegalStateException(
              getClass().getName() + ".sizeOf() is reporting inconsistent results!");
        }

        if (size <= maxSize || map.isEmpty()) {
          break;
        }

        Map.Entry<K, V> toEvict = map.entrySet().iterator().next();
        key = toEvict.getKey();
        value = toEvict.getValue();
        map.remove(key);
        size -= safeSizeOf(key, value);
        evictionCount++;
      }

      entryRemoved(true, key, value, null);
    }
  }

  /**
   * Removes the entry for {@code key} if it exists.
   *
   * @return the previous value mapped by {@code key}.
   */
  public final V remove(K key) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    V previous;
    synchronized (this) {
      previous = map.remove(key);
      if (previous != null) {
        size -= safeSizeOf(key, previous);
      }
    }

    if (previous != null) {
      entryRemoved(false, key, previous, null);
    }

    return previous;
  }

  /**
   * Called for entries that have been evicted or removed. This method is
   * invoked when a value is evicted to make space, removed by a call to
   * {@link #remove}, or replaced by a call to {@link #put}. The default
   * implementation does nothing.
   *
   * <p>The method is called without synchronization: other threads may
   * access the cache while this method is executing.
   *
   * @param evicted true if the entry is being removed to make space, false
   * if the removal was caused by a {@link #put} or {@link #remove}.
   * @param newValue the new value for {@code key}, if it exists. If non-null,
   * this removal was caused by a {@link #put}. Otherwise it was caused by
   * an eviction or a {@link #remove}.
   */
  protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
  }

  /**
   * Called after a cache miss to compute a value for the corresponding key.
   * Returns the computed value or null if no value can be computed. The
   * default implementation returns null.
   *
   * <p>The method is called without synchronization: other threads may
   * access the cache while this method is executing.
   *
   * <p>If a value for {@code key} exists in the cache when this method
   * returns, the created value will be released with {@link #entryRemoved}
   * and discarded. This can occur when multiple threads request the same key
   * at the same time (causing multiple values to be created), or when one
   * thread calls {@link #put} while another is creating a value for the same
   * key.
   */
  protected V create(K key) {
    return null;
  }

  private int safeSizeOf(K key, V value) {
    int result = sizeOf(key, value);
    if (result < 0) {
      throw new IllegalStateException("Negative size: " + key + "=" + value);
    }
    return result;
  }

  /**
   * Returns the size of the entry for {@code key} and {@code value} in
   * user-defined units.  The default implementation returns 1 so that size
   * is the number of entries and max size is the maximum number of entries.
   *
   * <p>An entry's size must not change while it is in the cache.
   */
  protected int sizeOf(K key, V value) {
    return 1;
  }

  /** Clear the cache, calling {@link #entryRemoved} on each removed entry. */
  public final void evictAll() {
    trimToSize(-1); // -1 will evict 0-sized elements
  }

  /**
   * For caches that do not override {@link #sizeOf}, this returns the number
   * of entries in the cache. For all other caches, this returns the sum of
   * the sizes of the entries in this cache.
   */
  public synchronized final int size() {
    return size;
  }

  /**
   * For caches that do not override {@link #sizeOf}, this returns the maximum
   * number of entries in the cache. For all other caches, this returns the
   * maximum sum of the sizes of the entries in this cache.
   */
  public synchronized final int maxSize() {
    return maxSize;
  }

  /** Returns the number of times {@link #get} returned a value. */
  public synchronized final int hitCount() {
    return hitCount;
  }

  /**
   * Returns the number of times {@link #get} returned null or required a new
   * value to be created.
   */
  public synchronized final int missCount() {
    return missCount;
  }

  /** Returns the number of times {@link #create(Object)} returned a value. */
  public synchronized final int createCount() {
    return createCount;
  }

  /** Returns the number of times {@link #put} was called. */
  public synchronized final int putCount() {
    return putCount;
  }

  /** Returns the number of values that have been evicted. */
  public synchronized final int evictionCount() {
    return evictionCount;
  }

  /**
   * Returns a copy of the current contents of the cache, ordered from least
   * recently accessed to most recently accessed.
   */
  public synchronized final Map<K, V> snapshot() {
    return new LinkedHashMap<K, V>(map);
  }

  @Override public synchronized final String toString() {
    int accesses = hitCount + missCount;
    int hitPercent = accesses != 0 ? (100 * hitCount / accesses) : 0;
    return String.format("LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]", maxSize, hitCount,
        missCount, hitPercent);
  }
}
