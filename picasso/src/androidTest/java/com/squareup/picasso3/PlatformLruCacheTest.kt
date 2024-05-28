/*
 * Copyright (C) 2011 Square, Inc.
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
import android.graphics.Bitmap.Config.ALPHA_8
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformLruCacheTest {
  // The use of ALPHA_8 simplifies the size math in tests since only one byte is used per-pixel.
  private val bitmapA = Bitmap.createBitmap(1, 1, ALPHA_8)
  private val bitmapB = Bitmap.createBitmap(1, 1, ALPHA_8)
  private val bitmapC = Bitmap.createBitmap(1, 1, ALPHA_8)
  private val bitmapD = Bitmap.createBitmap(1, 1, ALPHA_8)
  private val bitmapE = Bitmap.createBitmap(1, 1, ALPHA_8)

  private var expectedPutCount = 0
  private var expectedHitCount = 0
  private var expectedMissCount = 0
  private var expectedEvictionCount = 0

  @Test fun testStatistics() {
    val cache = PlatformLruCache(3)
    assertStatistics(cache)

    cache["a"] = bitmapA
    expectedPutCount++
    assertStatistics(cache)
    assertHit(cache, "a", bitmapA)

    cache["b"] = bitmapB
    expectedPutCount++
    assertStatistics(cache)
    assertHit(cache, "a", bitmapA)
    assertHit(cache, "b", bitmapB)
    assertSnapshot(cache, "a", bitmapA, "b", bitmapB)

    cache["c"] = bitmapC
    expectedPutCount++
    assertStatistics(cache)
    assertHit(cache, "a", bitmapA)
    assertHit(cache, "b", bitmapB)
    assertHit(cache, "c", bitmapC)
    assertSnapshot(cache, "a", bitmapA, "b", bitmapB, "c", bitmapC)

    cache["d"] = bitmapD
    expectedPutCount++
    expectedEvictionCount++ // a should have been evicted
    assertStatistics(cache)
    assertMiss(cache, "a")
    assertHit(cache, "b", bitmapB)
    assertHit(cache, "c", bitmapC)
    assertHit(cache, "d", bitmapD)
    assertHit(cache, "b", bitmapB)
    assertHit(cache, "c", bitmapC)
    assertSnapshot(cache, "d", bitmapD, "b", bitmapB, "c", bitmapC)

    cache["e"] = bitmapE
    expectedPutCount++
    expectedEvictionCount++ // d should have been evicted
    assertStatistics(cache)
    assertMiss(cache, "d")
    assertMiss(cache, "a")
    assertHit(cache, "e", bitmapE)
    assertHit(cache, "b", bitmapB)
    assertHit(cache, "c", bitmapC)
    assertSnapshot(cache, "e", bitmapE, "b", bitmapB, "c", bitmapC)
  }

  @Test fun evictionWithSingletonCache() {
    val cache = PlatformLruCache(1)
    cache["a"] = bitmapA
    cache["b"] = bitmapB
    assertSnapshot(cache, "b", bitmapB)
  }

  /**
   * Replacing the value for a key doesn't cause an eviction but it does bring the replaced entry to
   * the front of the queue.
   */
  @Test fun putCauseEviction() {
    val cache = PlatformLruCache(3)

    cache["a"] = bitmapA
    cache["b"] = bitmapB
    cache["c"] = bitmapC
    cache["b"] = bitmapD
    assertSnapshot(cache, "a", bitmapA, "c", bitmapC, "b", bitmapD)
  }

  @Test fun evictAll() {
    val cache = PlatformLruCache(4)
    cache["a"] = bitmapA
    cache["b"] = bitmapB
    cache["c"] = bitmapC
    cache.clear()
    assertThat(cache.cache.snapshot()).isEmpty()
  }

  @Test fun clearPrefixedKey() {
    val cache = PlatformLruCache(3)

    cache["Hello\nAlice!"] = bitmapA
    cache["Hello\nBob!"] = bitmapB
    cache["Hello\nEve!"] = bitmapC
    cache["Hellos\nWorld!"] = bitmapD

    cache.clearKeyUri("Hello")
    assertThat(cache.cache.snapshot()).hasSize(1)
    assertThat(cache.cache.snapshot()).containsKey("Hellos\nWorld!")
  }

  @Test fun invalidate() {
    val cache = PlatformLruCache(3)
    cache["Hello\nAlice!"] = bitmapA
    assertThat(cache.size()).isEqualTo(1)
    cache.clearKeyUri("Hello")
    assertThat(cache.size()).isEqualTo(0)
  }

  @Test fun overMaxSizeDoesNotClear() {
    val cache = PlatformLruCache(16)
    val size4 = Bitmap.createBitmap(2, 2, ALPHA_8)
    val size16 = Bitmap.createBitmap(4, 4, ALPHA_8)
    val size25 = Bitmap.createBitmap(5, 5, ALPHA_8)
    cache["4"] = size4
    expectedPutCount++
    assertHit(cache, "4", size4)
    cache["16"] = size16
    expectedPutCount++
    expectedEvictionCount++ // size4 was evicted.
    assertMiss(cache, "4")
    assertHit(cache, "16", size16)
    cache["25"] = size25
    assertHit(cache, "16", size16)
    assertMiss(cache, "25")
    assertThat(cache.size()).isEqualTo(16)
  }

  @Test fun overMaxSizeRemovesExisting() {
    val cache = PlatformLruCache(20)
    val size4 = Bitmap.createBitmap(2, 2, ALPHA_8)
    val size16 = Bitmap.createBitmap(4, 4, ALPHA_8)
    val size25 = Bitmap.createBitmap(5, 5, ALPHA_8)
    cache["small"] = size4
    expectedPutCount++
    assertHit(cache, "small", size4)
    cache["big"] = size16
    expectedPutCount++
    assertHit(cache, "small", size4)
    assertHit(cache, "big", size16)
    cache["big"] = size25
    assertHit(cache, "small", size4)
    assertMiss(cache, "big")
    assertThat(cache.size()).isEqualTo(4)
  }

  private fun assertHit(cache: PlatformLruCache, key: String, value: Bitmap) {
    assertThat(cache[key]).isEqualTo(value)
    expectedHitCount++
    assertStatistics(cache)
  }

  private fun assertMiss(cache: PlatformLruCache, key: String) {
    assertThat(cache[key]).isNull()
    expectedMissCount++
    assertStatistics(cache)
  }

  private fun assertStatistics(cache: PlatformLruCache) {
    assertThat(cache.putCount()).isEqualTo(expectedPutCount)
    assertThat(cache.hitCount()).isEqualTo(expectedHitCount)
    assertThat(cache.missCount()).isEqualTo(expectedMissCount)
    assertThat(cache.evictionCount()).isEqualTo(expectedEvictionCount)
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun assertSnapshot(cache: PlatformLruCache, vararg keysAndValues: Any) {
    val actualKeysAndValues = buildList {
      cache.cache.snapshot().forEach { (key, value) ->
        add(key)
        add(value.bitmap)
      }
    }

    // assert using lists because order is important for LRUs
    assertThat(actualKeysAndValues).isEqualTo(listOf(*keysAndValues))
  }
}
