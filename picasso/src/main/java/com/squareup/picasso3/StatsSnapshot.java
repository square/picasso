/*
 * Copyright (C) 2013 Square, Inc.
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.IOException;
import okio.Buffer;
import okio.BufferedSink;

import static com.squareup.picasso3.Picasso.TAG;

/** Represents all stats for a {@link Picasso} instance at a single point in time. */
public final class StatsSnapshot {
  public final int maxSize;
  public final int size;
  public final long cacheHits;
  public final long cacheMisses;
  public final long totalDownloadSize;
  public final long totalOriginalBitmapSize;
  public final long totalTransformedBitmapSize;
  public final long averageDownloadSize;
  public final long averageOriginalBitmapSize;
  public final long averageTransformedBitmapSize;
  public final int downloadCount;
  public final int originalBitmapCount;
  public final int transformedBitmapCount;

  public final long timeStamp;

  StatsSnapshot(int maxSize, int size, long cacheHits, long cacheMisses,
      long totalDownloadSize, long totalOriginalBitmapSize, long totalTransformedBitmapSize,
      long averageDownloadSize, long averageOriginalBitmapSize, long averageTransformedBitmapSize,
      int downloadCount, int originalBitmapCount, int transformedBitmapCount, long timeStamp) {
    this.maxSize = maxSize;
    this.size = size;
    this.cacheHits = cacheHits;
    this.cacheMisses = cacheMisses;
    this.totalDownloadSize = totalDownloadSize;
    this.totalOriginalBitmapSize = totalOriginalBitmapSize;
    this.totalTransformedBitmapSize = totalTransformedBitmapSize;
    this.averageDownloadSize = averageDownloadSize;
    this.averageOriginalBitmapSize = averageOriginalBitmapSize;
    this.averageTransformedBitmapSize = averageTransformedBitmapSize;
    this.downloadCount = downloadCount;
    this.originalBitmapCount = originalBitmapCount;
    this.transformedBitmapCount = transformedBitmapCount;
    this.timeStamp = timeStamp;
  }

  /** Prints out this {@link StatsSnapshot} into log. */
  @SuppressWarnings("UnusedDeclaration") public void dump() {
    Buffer buffer = new Buffer();
    try {
      dump(buffer);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    Log.i(TAG, buffer.readUtf8());
  }

  /** Writes this {@link StatsSnapshot} to the provided {@link BufferedSink}. */
  public void dump(@NonNull BufferedSink sink) throws IOException {
    sink.writeUtf8("===============BEGIN PICASSO STATS ===============");
    sink.writeUtf8("\n");
    sink.writeUtf8("Memory Cache Stats");
    sink.writeUtf8("\n");
    sink.writeUtf8("  Max Cache Size: ");
    sink.writeUtf8(Integer.toString(maxSize));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Cache Size: ");
    sink.writeUtf8(Integer.toString(size));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Cache % Full: ");
    sink.writeUtf8(Integer.toString((int) Math.ceil((float) size / maxSize * 100)));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Cache Hits: ");
    sink.writeUtf8(Long.toString(cacheHits));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Cache Misses: ");
    sink.writeUtf8(Long.toString(cacheMisses));
    sink.writeUtf8("\n");
    sink.writeUtf8("Network Stats");
    sink.writeUtf8("\n");
    sink.writeUtf8("  Download Count: ");
    sink.writeUtf8(Integer.toString(downloadCount));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Total Download Size: ");
    sink.writeUtf8(Long.toString(totalDownloadSize));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Average Download Size: ");
    sink.writeUtf8(Long.toString(averageDownloadSize));
    sink.writeUtf8("\n");
    sink.writeUtf8("Bitmap Stats");
    sink.writeUtf8("\n");
    sink.writeUtf8("  Total Bitmaps Decoded: ");
    sink.writeUtf8(Integer.toString(originalBitmapCount));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Total Bitmap Size: ");
    sink.writeUtf8(Long.toString(totalOriginalBitmapSize));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Total Transformed Bitmaps: ");
    sink.writeUtf8(Integer.toString(transformedBitmapCount));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Total Transformed Bitmap Size: ");
    sink.writeUtf8(Long.toString(totalTransformedBitmapSize));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Average Bitmap Size: ");
    sink.writeUtf8(Long.toString(averageOriginalBitmapSize));
    sink.writeUtf8("\n");
    sink.writeUtf8("  Average Transformed Bitmap Size: ");
    sink.writeUtf8(Long.toString(averageTransformedBitmapSize));
    sink.writeUtf8("\n");
    sink.writeUtf8("===============END PICASSO STATS ===============");
    sink.writeUtf8("\n");
  }

  @Nullable
  @Override public String toString() {
    return "StatsSnapshot{"
        + "maxSize="
        + maxSize
        + ", size="
        + size
        + ", cacheHits="
        + cacheHits
        + ", cacheMisses="
        + cacheMisses
        + ", downloadCount="
        + downloadCount
        + ", totalDownloadSize="
        + totalDownloadSize
        + ", averageDownloadSize="
        + averageDownloadSize
        + ", totalOriginalBitmapSize="
        + totalOriginalBitmapSize
        + ", totalTransformedBitmapSize="
        + totalTransformedBitmapSize
        + ", averageOriginalBitmapSize="
        + averageOriginalBitmapSize
        + ", averageTransformedBitmapSize="
        + averageTransformedBitmapSize
        + ", originalBitmapCount="
        + originalBitmapCount
        + ", transformedBitmapCount="
        + transformedBitmapCount
        + ", timeStamp="
        + timeStamp
        + '}';
  }
}
