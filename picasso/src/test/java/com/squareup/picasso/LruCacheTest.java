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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static android.graphics.Bitmap.Config.ALPHA_8;
import static junit.framework.Assert.fail;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class LruCacheTest {
  // The use of ALPHA_8 simplifies the size math in tests since only one byte is used per-pixel.
  private final Bitmap A = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap B = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap C = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap D = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap E = Bitmap.createBitmap(1, 1, ALPHA_8);

  private int expectedPutCount;
  private int expectedHitCount;
  private int expectedMissCount;
  private int expectedEvictionCount;

  @Test public void testStatistics() {
    LruCache cache = new LruCache(3);
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
    assertSnapshot(cache, "d", D, "b", B, "c", C);

    cache.set("e", E);
    expectedPutCount++;
    expectedEvictionCount++; // d should have been evicted
    assertStatistics(cache);
    assertMiss(cache, "d");
    assertMiss(cache, "a");
    assertHit(cache, "e", E);
    assertHit(cache, "b", B);
    assertHit(cache, "c", C);
    assertSnapshot(cache, "e", E, "b", B, "c", C);
  }

  @Test public void constructorDoesNotAllowZeroCacheSize() {
    try {
      new LruCache(0);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void cannotPutNullKey() {
    LruCache cache = new LruCache(3);
    try {
      cache.set(null, A);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void cannotPutNullValue() {
    LruCache cache = new LruCache(3);
    try {
      cache.set("a", null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void evictionWithSingletonCache() {
    LruCache cache = new LruCache(1);
    cache.set("a", A);
    cache.set("b", B);
    assertSnapshot(cache, "b", B);
  }

  /**
   * Replacing the value for a key doesn't cause an eviction but it does bring the replaced entry to
   * the front of the queue.
   */
  @Test public void putCauseEviction() {
    LruCache cache = new LruCache(3);

    cache.set("a", A);
    cache.set("b", B);
    cache.set("c", C);
    cache.set("b", D);
    assertSnapshot(cache, "a", A, "c", C, "b", D);
  }

  @Test public void evictAll() {
    LruCache cache = new LruCache(4);
    cache.set("a", A);
    cache.set("b", B);
    cache.set("c", C);
    cache.evictAll();
    assertThat(cache.map).isEmpty();
  }

  private void assertHit(LruCache cache, String key, Bitmap value) {
    assertThat(cache.get(key)).isEqualTo(value);
    expectedHitCount++;
    assertStatistics(cache);
  }

  private void assertMiss(LruCache cache, String key) {
    assertThat(cache.get(key)).isNull();
    expectedMissCount++;
    assertStatistics(cache);
  }

  private void assertStatistics(LruCache cache) {
    assertThat(cache.putCount()).isEqualTo(expectedPutCount);
    assertThat(cache.hitCount()).isEqualTo(expectedHitCount);
    assertThat(cache.missCount()).isEqualTo(expectedMissCount);
    assertThat(cache.evictionCount()).isEqualTo(expectedEvictionCount);
  }

  private void assertSnapshot(LruCache cache, Object... keysAndValues) {
    List<Object> actualKeysAndValues = new ArrayList<Object>();
    for (Map.Entry<String, Bitmap> entry : cache.map.entrySet()) {
      actualKeysAndValues.add(entry.getKey());
      actualKeysAndValues.add(entry.getValue());
    }

    // assert using lists because order is important for LRUs
    assertThat(actualKeysAndValues).isEqualTo(Arrays.asList(keysAndValues));
  }
}
