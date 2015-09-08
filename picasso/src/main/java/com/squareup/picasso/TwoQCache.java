package com.squareup.picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 2Q: A Low Overhead High Performance Buffer Management Replacement Algorith
 * Based on description: http://www.vldb.org/conf/1994/P439.PDF
 * Created by recoilme on 22/08/15.
 * email: vadim-kulibaba@yandex.ru
 */
public class TwoQCache<K, V> {
  /**
   * Primary container
   */
  final HashMap<K, V> map;
  /**
   * Sets for 2Q algorithm
   */
  private final LinkedHashSet<K> mapIn, mapOut, mapHot;

  protected float quarter = .25f;
  /**
   * Size of this cache in units. Not necessarily the number of elements.
   */
  //private int size;
  private int sizeIn;
  private int sizeOut;
  private int sizeHot;

  private int maxSizeIn;
  private int maxSizeOut;
  private int maxSizeHot;

  private int putCount;
  private int createCount;
  private int evictionCount;
  private int hitCount;
  private int missCount;

  /**
   * Two queues cache
   *
   * @param maxSize for caches that do not override {@link #sizeOf}, this is
   *                this is the maximum sum of the sizes of the entries in this cache.
   */
  public TwoQCache(int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize <= 0");
    }

    calcMaxSizes(maxSize);

    map = new HashMap<K, V>(0, 0.75f);

    mapIn = new LinkedHashSet<K>();
    mapOut = new LinkedHashSet<K>();
    mapHot = new LinkedHashSet<K>();
  }

  /**
   * Sets sizes:
   * mapIn  ~ 25% // 1st lvl - store for input keys, FIFO
   * mapOut ~ 50% // 2nd lvl - store for keys goes from input to output, FIFO
   * mapHot ~ 25% // hot lvl - store for keys goes from output to hot, LRU
   *
   * @param maxSize if mapIn/mapOut sizes == 0, works like LRU for mapHot
   */
  private void calcMaxSizes(int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize <= 0");
    }
    synchronized (this) {
      //sizes
      maxSizeIn = (int) (maxSize * quarter);
      maxSizeOut = maxSizeIn * 2;
      maxSizeHot = maxSize - maxSizeOut - maxSizeIn;
    }
  }

  /**
   * Sets the size of the cache.
   *
   * @param maxSize The new maximum size.
   */
  public void resize(int maxSize) {

    calcMaxSizes(maxSize);
    synchronized (this) {
      HashMap<K, V> copy = new HashMap<K, V>(map);
      evictAll();
      Iterator<K> it = copy.keySet().iterator();
      while (it.hasNext()) {
        K key = it.next();
        put(key, copy.get(key));
      }
    }
  }

  /**
   * Returns the value for {@code key} if it exists in the cache or can be
   * created by {@code #create}. If a value was returned, it is moved to the
   * head of the queue. This returns null if a value is not cached and cannot
   * be created.
   */
  public V get(K key) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    V mapValue;
    synchronized (this) {
      mapValue = map.get(key);
      if (mapValue != null) {
        hitCount++;
        if (mapHot.contains(key)) {
          // add & trim (LRU)
          mapHot.remove(key);
          mapHot.add(key);
        } else {
          if (mapOut.contains(key)) {
            mapHot.add(key);
            sizeHot += safeSizeOf(key, mapValue);
            trimMapHot();
            sizeOut -= safeSizeOf(key, mapValue);
            mapOut.remove(key);
          }
        }
        return mapValue;
      }
      missCount++;
    }

        /*
         * Attempt to create a value. This may take a long time, and the map
         * may be different when create() returns. If a conflicting value was
         * added to the map while create() was working, we leave that value in
         * the map and release the created value.
         */

    V createdValue = create(key);
    if (createdValue == null) {
      return null;
    }

    synchronized (this) {
      createCount++;

      if (!map.containsKey(key)) {
        // There was no conflict, create
        return put(key, createdValue);
      } else {
        return map.get(key);
      }
    }
  }

  /**
   * Caches {@code value} for {@code key}.
   *
   * @return the previous value mapped by {@code key}.
   */
  public V put(K key, V value) {
    if (key == null || value == null) {
      throw new NullPointerException("key == null || value == null");
    }

    if (map.containsKey(key)) {
      // if already have - replace it.
      // Cache size may be overheaded at this moment
      synchronized (this) {
        V oldValue = map.get(key);
        if (mapIn.contains(key)) {
          sizeIn -= safeSizeOf(key, oldValue);
          sizeIn += safeSizeOf(key, value);
        }
        if (mapOut.contains(key)) {
          sizeOut -= safeSizeOf(key, oldValue);
          sizeOut += safeSizeOf(key, value);
        }
        if (mapHot.contains(key)) {
          sizeHot -= safeSizeOf(key, oldValue);
          sizeHot += safeSizeOf(key, value);
        }
      }
      return map.put(key, value);
    }
    V result;
    synchronized (this) {
      putCount++;
      final int sizeOfValue = safeSizeOf(key, value);
      //if there are free page slots then put value into a free page slot
      boolean hasFreeSlot = add2slot(key, safeSizeOf(key, value));
      if (hasFreeSlot) {
        // add 2 free slot & exit
        map.put(key, value);
        result = value;
      } else {
        // no free slot, go to trim mapIn/mapOut
        if (trimMapIn(sizeOfValue)) {
          //put X into the reclaimed page slot
          map.put(key, value);
          result = value;
        } else {
          map.put(key, value);
          mapHot.add(key);
          sizeHot += safeSizeOf(key, value);
          trimMapHot();
          result = value;
        }
      }

    }
    return result;
  }

  /**
   * Remove items by LRU from mapHot
   */
  public void trimMapHot() {
    while (true) {
      K key;
      V value;
      synchronized (this) {
        if (sizeHot < 0 || (mapHot.isEmpty() && sizeHot != 0)) {
          throw new IllegalStateException(getClass().getName()
                  + ".sizeOf() is reporting inconsistent results!");
        }

        if (sizeHot <= maxSizeHot || mapHot.isEmpty()) {
          break;
        }
        // we add new item before, so next return first (LRU) item
        key = mapHot.iterator().next();
        mapHot.remove(key);
        value = map.get(key);
        sizeHot -= safeSizeOf(key, value);
        map.remove(key);
        evictionCount++;
      }
      entryRemoved(true, key, value, null);
    }
  }

  /**
   * Remove items by FIFO from mapIn & mapOut
   *
   * @param sizeOfValue size of
   * @return boolean is trim
   */
  private boolean trimMapIn(final int sizeOfValue) {
    boolean result = false;
    if (maxSizeIn < sizeOfValue) {
      return result;
    } else {
      while (mapIn.iterator().hasNext()) {
        K keyIn;
        V valueIn;
        if (!mapIn.iterator().hasNext()) {
          System.out.print("err");
        }
        keyIn = mapIn.iterator().next();
        valueIn = map.get(keyIn);
        if ((sizeIn + sizeOfValue) <= maxSizeIn || mapIn.isEmpty()) {
          //put X into the reclaimed page slot
          if (keyIn == null) {
            System.out.print("err");
          }
          mapIn.add(keyIn);
          sizeIn += sizeOfValue;
          result = true;
          break;
        }
        //page out the tail of mapIn, call it Y
        mapIn.remove(keyIn);
        final int removedItemSize = safeSizeOf(keyIn, valueIn);
        sizeIn -= removedItemSize;

        // add identifier of Y to the head of mapOut
        while (mapOut.iterator().hasNext()) {
          K keyOut;
          V valueOut;
          if ((sizeOut + removedItemSize) <= maxSizeOut || mapOut.isEmpty()) {
            // put Y into the reclaimed page slot
            mapOut.add(keyIn);
            sizeOut += removedItemSize;
            break;
          }
          //remove identifier of Z from the tail of mapOut
          keyOut = mapOut.iterator().next();
          mapOut.remove(keyOut);
          valueOut = map.get(keyOut);
          sizeOut -= safeSizeOf(keyOut, valueOut);
        }
      }
    }
    return result;
  }

  /**
   * Check for free slot in any container and add if exists
   *
   * @param key key
   * @param sizeOfValue size
   * @return true if key added
   */
  private boolean add2slot(final K key, final int sizeOfValue) {
    boolean hasFreeSlot = false;
    if (!hasFreeSlot && maxSizeIn >= sizeIn + sizeOfValue) {
      mapIn.add(key);
      sizeIn += sizeOfValue;
      hasFreeSlot = true;
    }
    if (!hasFreeSlot && maxSizeOut >= sizeOut + sizeOfValue) {
      mapOut.add(key);
      sizeOut += sizeOfValue;
      hasFreeSlot = true;
    }
    if (!hasFreeSlot && maxSizeHot >= sizeHot + sizeOfValue) {
      mapHot.add(key);
      sizeHot += sizeOfValue;
      hasFreeSlot = true;
    }
    return hasFreeSlot;
  }


  /**
   * Removes the entry for {@code key} if it exists.
   *
   * @return the previous value mapped by {@code key}.
   */
  public V remove(K key) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    V previous;
    synchronized (this) {
      previous = map.remove(key);
      if (previous != null) {
        if (mapIn.contains(key)) {
          sizeIn -= safeSizeOf(key, previous);
          mapIn.remove(key);
        }
        if (mapOut.contains(key)) {
          sizeOut -= safeSizeOf(key, previous);
          mapOut.remove(key);
        }
        if (mapHot.contains(key)) {
          sizeHot -= safeSizeOf(key, previous);
          mapHot.remove(key);
        }
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
   * <p>
   * <p>The method is called without synchronization: other threads may
   * access the cache while this method is executing.
   *
   * @param evicted  true if the entry is being removed to make space, false
   *                 if the removal was caused by a {@link #put} or {@link #remove}.
   * @param newValue the new value for {@code key}, if it exists. If non-null,
   *                 this removal was caused by a {@link #put}. Otherwise it was caused by
   *                 an eviction or a {@link #remove}.
   */
  protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
  }

  /**
   * Called after a cache miss to compute a value for the corresponding key.
   * Returns the computed value or null if no value can be computed. The
   * default implementation returns null.
   * <p>
   * <p>The method is called without synchronization: other threads may
   * access the cache while this method is executing.
   * <p>
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
   * <p>
   * <p>An entry's size must not change while it is in the cache.
   */
  protected int sizeOf(K key, V value) {
    return 1;
  }

  /**
   * Clear the cache, calling {@link #entryRemoved} on each removed entry.
   */
  public synchronized final void evictAll() {
    Iterator<K> it = map.keySet().iterator();
    while (it.hasNext()) {
      K key = it.next();
      it.remove();
      remove(key);
    }
    mapIn.clear();
    mapOut.clear();
    mapHot.clear();
    sizeIn = 0;
    sizeOut = 0;
    sizeHot = 0;
  }

  /**
   * For caches that do not override {@link #sizeOf}, this returns the number
   * of entries in the cache. For all other caches, this returns the sum of
   * the sizes of the entries in this cache.
   */
  public synchronized final int size() {
    return sizeIn + sizeOut + sizeHot;
  }

  /**
   * For caches that do not override {@link #sizeOf}, this returns the maximum
   * number of entries in the cache. For all other caches, this returns the
   * maximum sum of the sizes of the entries in this cache.
   */
  public synchronized final int maxSize() {
    return maxSizeIn + maxSizeOut + maxSizeHot;
  }

  /**
   * Returns the number of times {@link #get} returned a value that was
   * already present in the cache.
   */
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

  /**
   * Returns the number of times {@link #create(Object)} returned a value.
   */
  public synchronized final int createCount() {
    return createCount;
  }

  /**
   * Returns the number of times {@link #put} was called.
   */
  public synchronized final int putCount() {
    return putCount;
  }

  /**
   * Returns the number of values that have been evicted.
   */
  public synchronized final int evictionCount() {
    return evictionCount;
  }

  /**
   * Returns a copy of the current contents of the cache, ordered from least
   * recently accessed to most recently accessed.
   */
  public synchronized final Map<K, V> snapshot() {
    return new HashMap<K, V>(map);
  }

  @Override
  public synchronized final String toString() {
    int accesses = hitCount + missCount;
    int hitPercent = accesses != 0 ? (100 * hitCount / accesses) : 0;
    return String.format("Cache[size=%d,maxSize=%d,hits=%d,misses=%d,hitRate=%d%%," +
                    "]",
            size(), maxSize(), hitCount, missCount, hitPercent)
            + "\n map:" + map.toString();
  }

  public List<Object> getMapHotSnapshot() {
    List<Object> result = new ArrayList<Object>();
    Iterator<K> it = mapHot.iterator();
    while (it.hasNext()) {
      K key = it.next();
      result.add(key);
      result.add(map.get(key));
    }
    return result;
  }
}
