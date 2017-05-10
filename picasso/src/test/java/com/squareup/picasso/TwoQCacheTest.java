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

package com.squareup.picasso;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static android.graphics.Bitmap.Config.ALPHA_8;
import static android.graphics.Bitmap.Config.ARGB_8888;
import static junit.framework.Assert.fail;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TwoQCacheTest {
  // The use of ALPHA_8 simplifies the size math in tests since only one byte is used per-pixel.
  private final Bitmap A = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap B = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap C = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap D = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap E = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap AVATAR = Bitmap.createBitmap(50, 50, ARGB_8888);
  private final Bitmap SMALL = Bitmap.createBitmap(400, 300, ARGB_8888);
  private final Bitmap MEDIUM = Bitmap.createBitmap(800, 600, ARGB_8888);

  private int expectedPutCount;
  private int expectedHitCount;
  private int expectedMissCount;
  private int expectedEvictionCount;

  @Test public void testStatistics() {
    TwoQCacheImpl cache = new TwoQCacheImpl(3);
    assertStatistics(cache);

    cache.set("a", A);
    expectedPutCount++;
    assertStatistics(cache);
    assertHit(cache, "a", A);

    cache.set("b", B);
    expectedPutCount++;
    assertStatistics(cache);
    assertHit(cache, "a", A);
    assertHit(cache, "b", B);
    assertSnapshot(cache, "a", A, "b", B);

    cache.set("c", C);
    expectedPutCount++;
    assertStatistics(cache);
    assertHit(cache, "a", A);
    assertHit(cache, "b", B);
    assertHit(cache, "c", C);
    assertSnapshot(cache, "a", A, "b", B, "c", C);

    cache.set("d", D);
    expectedPutCount++;
    expectedEvictionCount++; // a should have been evicted
    assertStatistics(cache);
    assertMiss(cache, "a");
    assertHit(cache, "b", B);
    assertHit(cache, "c", C);
    assertHit(cache, "d", D);
    assertHit(cache, "b", B);
    assertHit(cache, "c", C);
    assertMapHotSnapshot(cache, "d", D, "b", B, "c", C);

    cache.set("e", E);
    expectedPutCount++;
    expectedEvictionCount++; // d should have been evicted
    assertStatistics(cache);
    assertMiss(cache, "d");
    assertMiss(cache, "a");
    assertHit(cache, "e", E);
    assertHit(cache, "b", B);
    assertHit(cache, "c", C);
    assertMapHotSnapshot(cache, "e", E, "b", B, "c", C);
  }

  @Test public void constructorDoesNotAllowZeroCacheSize() {
    try {
      new TwoQCacheImpl(0);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void cannotPutNullKey() {
    TwoQCacheImpl cache = new TwoQCacheImpl(3);
    try {
      cache.set(null, A);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void cannotPutNullValue() {
    TwoQCacheImpl cache = new TwoQCacheImpl(3);
    try {
      cache.set("a", null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void evictionWithSingletonCache() {
    TwoQCacheImpl cache = new TwoQCacheImpl(1);
    cache.set("a", A);
    cache.set("b", B);
    assertSnapshot(cache, "b", B);
  }

  @Test public void throwsWithNullKey() {
    TwoQCacheImpl cache = new TwoQCacheImpl(1);
    try {
      cache.get(null);
      fail("Expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  /**
   * Replacing the value for a key doesn't cause an eviction but it does bring the replaced entry to
   * the front of the queue.
   */
  @Test public void putCauseEviction() {
    TwoQCacheImpl cache = new TwoQCacheImpl(3);

    cache.set("a", A);
    cache.set("b", B);
    cache.set("c", C);
    cache.set("b", D);
    assertSnapshot(cache, "a", A, "b", D, "c", C);
  }

  @Test public void evictAll() {
    TwoQCacheImpl cache = new TwoQCacheImpl(4);
    cache.set("a", A);
    cache.set("b", B);
    cache.set("c", C);
    cache.evictAll();
    assertThat(cache.map).isEmpty();
  }

  @Test public void clearPrefixedKey() {
    TwoQCacheImpl cache = new TwoQCacheImpl(3);

    cache.set("Hello\nAlice!", A);
    cache.set("Hello\nBob!", B);
    cache.set("Hello\nEve!", C);
    cache.set("Hellos\nWorld!", D);

    cache.clearKeyUri("Hello");
    assertThat(cache.map).hasSize(1).containsKey("Hellos\nWorld!");
  }

  @Test public void invalidate() {
    TwoQCacheImpl cache = new TwoQCacheImpl(3);
    cache.set("Hello\nAlice!", A);
    assertThat(cache.size()).isEqualTo(1);
    cache.clearKeyUri("Hello");
    assertThat(cache.size()).isZero();
  }

  @Test public void testLruCache() {
    LruCache lruCache = new LruCache(1024 * 1024 * 10);
    long t = System.currentTimeMillis();
    for (int i=0; i<1000; i++) {
      String avatarKey = "avatar"+i % 10;
      lruCache.set(avatarKey, AVATAR);
      lruCache.get(avatarKey);
      lruCache.set("imageMedium" + i, MEDIUM);
      lruCache.get("imageMedium" + i);
      lruCache.set("imageSmall" + i, SMALL);
      lruCache.get("imageSmall" + i);
    }
    long lruTime = (System.currentTimeMillis() - t);
    System.out.println("lruCache time:"+lruTime);

    for (int i=0; i<10; i++) {
      String avatarKey = "avatar" + i;
      lruCache.get(avatarKey);
    }
    System.out.println("\nlruCache:" + lruCache.toString());
    //assertNotEquals("100% hit count impossible", 0, lruCache.missCount());

    TwoQCacheImpl twoQueues = new TwoQCacheImpl(1024 * 1024 * 10);

    t = System.currentTimeMillis();
    for (int i=0; i<1000; i++) {
      String avatarKey = "avatar"+i % 10;
      twoQueues.put(avatarKey,AVATAR);
      twoQueues.get(avatarKey);
      twoQueues.put("imageMedium" + i, MEDIUM);
      twoQueues.get("imageMedium" + i);
      twoQueues.put("imageSmall" + i, SMALL);
      twoQueues.get("imageSmall" + i);
    }
    long twoQueuesTime = (System.currentTimeMillis() - t);
    System.out.println("\ntwoQueuesCache time:"+twoQueuesTime);

    for (int i=0; i<10; i++) {
      String avatarKey = "avatar" + i;
      twoQueues.get(avatarKey);
    }
    System.out.println("twoQueues:"+twoQueues.toString());
    //"100% hit count"
    assertThat(twoQueues.missCount() == 0);

    twoQueues.resize(1024 * 1024);
    System.out.println(twoQueues.toString());
    //"Max size mismatch"
    assertThat(twoQueues.maxSize() == 1024 * 1024);

  }

  private void assertHit(TwoQCacheImpl cache, String key, Bitmap value) {
    assertThat(cache.get(key)).isEqualTo(value);
    expectedHitCount++;
    assertStatistics(cache);
  }

  private void assertMiss(TwoQCacheImpl cache, String key) {
    assertThat(cache.get(key)).isNull();
    expectedMissCount++;
    assertStatistics(cache);
  }

  private void assertStatistics(TwoQCacheImpl cache) {
    assertThat(cache.putCount()).isEqualTo(expectedPutCount);
    assertThat(cache.hitCount()).isEqualTo(expectedHitCount);
    assertThat(cache.missCount()).isEqualTo(expectedMissCount);
    assertThat(cache.evictionCount()).isEqualTo(expectedEvictionCount);
  }

  private void assertSnapshot(TwoQCacheImpl cache, Object... keysAndValues) {
    List<Object> actualKeysAndValues = new ArrayList<Object>();
    for (Map.Entry<String, Bitmap> entry : cache.map.entrySet()) {
      actualKeysAndValues.add(entry.getKey());
      actualKeysAndValues.add(entry.getValue());
    }

    // assert using lists because order is important for LRUs
    assertThat(actualKeysAndValues).isEqualTo(Arrays.asList(keysAndValues));
  }

  private void assertMapHotSnapshot(TwoQCacheImpl cache, Object... keysAndValues) {
    List<Object> actualKeysAndValues = cache.getMapHotSnapshot();
    // assert using lists because order is important for LRUs
    assertThat(actualKeysAndValues).isEqualTo(Arrays.asList(keysAndValues));
  }
}
