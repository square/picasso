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
package com.squareup.picasso;

import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Represents all stats for a {@link Picasso} instance at a single point in time. */
public class StatsSnapshot {
  private static final String TAG = "Picasso";

  public final int maxSize;
  public final int size;
  public final long cacheHits;
  public final long cacheMisses;
  public final long totalOriginalBitmapSize;
  public final long totalTransformedBitmapSize;
  public final long averageOriginalBitmapSize;
  public final long averageTransformedBitmapSize;
  public final int originalBitmapCount;
  public final int transformedBitmapCount;

  public final long timeStamp;

  public StatsSnapshot(int maxSize, int size, long cacheHits, long cacheMisses,
      long totalOriginalBitmapSize, long totalTransformedBitmapSize, long averageOriginalBitmapSize,
      long averageTransformedBitmapSize, int originalBitmapCount, int transformedBitmapCount,
      long timeStamp) {
    this.maxSize = maxSize;
    this.size = size;
    this.cacheHits = cacheHits;
    this.cacheMisses = cacheMisses;
    this.totalOriginalBitmapSize = totalOriginalBitmapSize;
    this.totalTransformedBitmapSize = totalTransformedBitmapSize;
    this.averageOriginalBitmapSize = averageOriginalBitmapSize;
    this.averageTransformedBitmapSize = averageTransformedBitmapSize;
    this.originalBitmapCount = originalBitmapCount;
    this.transformedBitmapCount = transformedBitmapCount;
    this.timeStamp = timeStamp;
  }

  /** Prints out this {@link StatsSnapshot} into log. */
  public void dump() {
    StringWriter logWriter = new StringWriter();
    dump(new PrintWriter(logWriter));
    Log.i(TAG, logWriter.toString());
  }

  /** Prints out this {@link StatsSnapshot} with the the provided {@link PrintWriter}. */
  public void dump(PrintWriter writer) {
    writer.println("===============BEGIN PICASSO STATS ===============");
    writer.println("Memory Cache Stats");
    writer.print("  Max Cache Size: ");
    writer.println(maxSize);
    writer.print("  Cache Size: ");
    writer.println(size);
    writer.print("  Cache % Full: ");
    writer.println((int) Math.ceil((float) size / maxSize * 100));
    writer.print("  Cache Hits: ");
    writer.println(cacheHits);
    writer.print("  Cache Misses: ");
    writer.println(cacheMisses);
    writer.println("Bitmap Stats");
    writer.print("  Total Bitmaps Decoded: ");
    writer.println(originalBitmapCount);
    writer.print("  Total Bitmap Size: ");
    writer.println(totalOriginalBitmapSize);
    writer.print("  Total Transformed Bitmaps: ");
    writer.println(transformedBitmapCount);
    writer.print("  Total Transformed Bitmap Size: ");
    writer.println(totalTransformedBitmapSize);
    writer.print("  Average Bitmap Size: ");
    writer.println(averageOriginalBitmapSize);
    writer.print("  Average Transformed Bitmap Size: ");
    writer.println(averageTransformedBitmapSize);
    writer.println("===============END PICASSO STATS ===============");
    writer.flush();
  }

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