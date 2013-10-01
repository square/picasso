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
import java.lang.ref.WeakReference;
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

@RunWith(RobolectricTestRunner.class) @Config(manifest = Config.NONE)
public class WeakReferencesCacheTest {
  // The use of ALPHA_8 simplifies the size math in tests since only one byte is used per-pixel.
  private final Bitmap A = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap B = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap C = Bitmap.createBitmap(1, 1, ALPHA_8);
  private final Bitmap NEW_VALUE_FOR_B = Bitmap.createBitmap(1, 1, ALPHA_8);
  private int expectedPutCount;
  private int expectedHitCount;
  private int expectedMissCount;

  @Test public void testStatistics() {
    WeakReferencesCache cache = new WeakReferencesCache();
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
    assertCacheHasExpectedValues(cache, "a", A, "b", B);

    cache.emulateGarbageRecollection();
    assertMiss(cache, "a");
    assertMiss(cache, "b");
  }

  @Test public void cannotPutNullKeyWillThrowException() {
    WeakReferencesCache cache = new WeakReferencesCache();
    try {
      cache.set(null, A);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void cannotPutNullValueWillThrowException() {
    WeakReferencesCache cache = new WeakReferencesCache();
    try {
      cache.set("a", null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  /**
   * Replacing the value for a key doesn't cause an eviction but it does bring the replaced entry
   * to
   * the front of the queue.
   */
  @Test public void putCauseEviction() {
    WeakReferencesCache cache = new WeakReferencesCache();

    cache.set("a", A);
    cache.set("b", B);
    cache.set("c", C);
    cache.set("b", NEW_VALUE_FOR_B);
    assertCacheHasExpectedValues(cache, "a", A, "c", C, "b", NEW_VALUE_FOR_B);
  }

  private void assertHit(WeakReferencesCache cache, String key, Bitmap value) {
    assertThat(cache.get(key)).isEqualTo(value);
    expectedHitCount++;
    assertStatistics(cache);
  }

  private void assertMiss(WeakReferencesCache cache, String key) {
    assertThat(cache.get(key)).isNull();
    expectedMissCount++;
    assertStatistics(cache);
  }

  private void assertStatistics(WeakReferencesCache cache) {
    assertThat(cache.putCount()).isEqualTo(expectedPutCount);
    assertThat(cache.hitCount()).isEqualTo(expectedHitCount);
    assertThat(cache.missCount()).isEqualTo(expectedMissCount);
  }

  private void assertCacheHasExpectedValues(WeakReferencesCache cache, Object... keysAndValues) {

    for (Map.Entry<String, WeakReference<Bitmap>> entry : cache.bitmapCache.entrySet()) {
      List<Object> actualKeysAndValues = new ArrayList<Object>();
      actualKeysAndValues.add(entry.getKey());
      actualKeysAndValues.add(entry.getValue().get());
      assertThat(Arrays.asList(keysAndValues)).containsAll(actualKeysAndValues);
    }

    //check that all info is in the cache. Size * 2 --> double because we have the key and the
    // value
    assertThat(keysAndValues.length).isEqualTo(cache.bitmapCache.size() * 2);
  }
}
