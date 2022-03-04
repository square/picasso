/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.picasso3.stats

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.BitmapCompat
import com.squareup.picasso3.EventListener
import com.squareup.picasso3.TAG
import okio.Buffer
import okio.BufferedSink
import java.io.IOException
import kotlin.math.ceil

class StatsEventListener : EventListener {
  private var maxCacheSize = 0
  private var cacheSize = 0

  private var cacheHits = 0L
  private var cacheMisses = 0L
  private var totalDownloadSize = 0L
  private var totalOriginalBitmapSize = 0L
  private var totalTransformedBitmapSize = 0L

  private var averageDownloadSize = 0.0
  private var averageOriginalBitmapSize = 0.0
  private var averageTransformedBitmapSize = 0.0

  private var downloadCount = 0
  private var originalBitmapCount = 0
  private var transformedBitmapCount = 0

  override fun cacheMaxSize(maxSize: Int) {
    maxCacheSize = maxSize
  }

  override fun cacheSize(size: Int) {
    cacheSize = size
  }

  override fun cacheHit() {
    cacheHits++
  }

  override fun cacheMiss() {
    cacheMisses++
  }

  override fun downloadFinished(size: Long) {
    downloadCount++
    totalDownloadSize += size
    averageDownloadSize = average(downloadCount, totalDownloadSize)
  }

  override fun bitmapDecoded(bitmap: Bitmap) {
    val bitmapSize = BitmapCompat.getAllocationByteCount(bitmap)

    originalBitmapCount++
    totalOriginalBitmapSize += bitmapSize
    averageOriginalBitmapSize = average(originalBitmapCount, totalOriginalBitmapSize)
  }

  override fun bitmapTransformed(bitmap: Bitmap) {
    val bitmapSize = BitmapCompat.getAllocationByteCount(bitmap)

    transformedBitmapCount++
    totalTransformedBitmapSize += bitmapSize
    averageTransformedBitmapSize = average(originalBitmapCount, totalTransformedBitmapSize)
  }

  fun getSnapshot() = Snapshot(
    maxCacheSize, cacheSize, cacheHits, cacheMisses,
    totalDownloadSize, totalOriginalBitmapSize, totalTransformedBitmapSize, averageDownloadSize,
    averageOriginalBitmapSize, averageTransformedBitmapSize, downloadCount, originalBitmapCount,
    transformedBitmapCount, System.currentTimeMillis()
  )

  private fun average(
    count: Int,
    totalSize: Long
  ): Double = totalSize * 1.0 / count

  data class Snapshot(
    val maxSize: Int,
    val size: Int,
    val cacheHits: Long,
    val cacheMisses: Long,
    val totalDownloadSize: Long,
    val totalOriginalBitmapSize: Long,
    val totalTransformedBitmapSize: Long,
    val averageDownloadSize: Double,
    val averageOriginalBitmapSize: Double,
    val averageTransformedBitmapSize: Double,
    val downloadCount: Int,
    val originalBitmapCount: Int,
    val transformedBitmapCount: Int,
    val timeStamp: Long
  ) {
    /** Prints out this [Snapshot] into log.  */
    fun dump() {
      val buffer = Buffer()
      try {
        dump(buffer)
      } catch (e: IOException) {
        throw AssertionError(e)
      }

      Log.i(TAG, buffer.readUtf8())
    }

    /** Writes this [Snapshot] to the provided [BufferedSink].  */
    @Throws(IOException::class)
    fun dump(sink: BufferedSink) {
      sink.writeUtf8("===============BEGIN PICASSO STATS ===============")
      sink.writeUtf8("\n")
      sink.writeUtf8("Memory Cache Stats")
      sink.writeUtf8("\n")
      sink.writeUtf8("  Max Cache Size: ")
      sink.writeUtf8(maxSize.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Cache Size: ")
      sink.writeUtf8(size.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Cache % Full: ")
      sink.writeUtf8(ceil((size.toDouble() / maxSize * 100)).toInt().toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Cache Hits: ")
      sink.writeUtf8(cacheHits.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Cache Misses: ")
      sink.writeUtf8(cacheMisses.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("Network Stats")
      sink.writeUtf8("\n")
      sink.writeUtf8("  Download Count: ")
      sink.writeUtf8(downloadCount.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Total Download Size: ")
      sink.writeUtf8(totalDownloadSize.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Average Download Size: ")
      sink.writeUtf8(averageDownloadSize.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("Bitmap Stats")
      sink.writeUtf8("\n")
      sink.writeUtf8("  Total Bitmaps Decoded: ")
      sink.writeUtf8(originalBitmapCount.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Total Bitmap Size: ")
      sink.writeUtf8(totalOriginalBitmapSize.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Total Transformed Bitmaps: ")
      sink.writeUtf8(transformedBitmapCount.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Total Transformed Bitmap Size: ")
      sink.writeUtf8(totalTransformedBitmapSize.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Average Bitmap Size: ")
      sink.writeUtf8(averageOriginalBitmapSize.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("  Average Transformed Bitmap Size: ")
      sink.writeUtf8(averageTransformedBitmapSize.toString())
      sink.writeUtf8("\n")
      sink.writeUtf8("===============END PICASSO STATS ===============")
      sink.writeUtf8("\n")
    }
  }
}
