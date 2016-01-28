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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream wrapper that supports unlimited independent cursors for
 * marking and resetting. Each cursor is a token, and it's the caller's
 * responsibility to keep track of these.
 */
final class MarkableInputStream extends InputStream {
  private static final int DEFAULT_BUFFER_SIZE = 4096;

  private final InputStream in;

  private long offset;
  private long reset;
  private long limit;
  private long defaultMark = -1;

  private boolean shouldGrowLimit = false;

  public MarkableInputStream(InputStream in) {
    this(in, DEFAULT_BUFFER_SIZE);
  }

  public MarkableInputStream(InputStream in, int size) {
    if (!in.markSupported()) {
      in = new BufferedInputStream(in, size);
    }
    this.in = in;
  }

  /** Marks this place in the stream so we can reset back to it later. */
  @Override public void mark(int readLimit) {
    defaultMark = savePosition(readLimit);
  }

  /**
   * Returns an opaque token representing the current position in the stream.
   * Call {@link #reset(long)} to return to this position in the stream later.
   * It is an error to call {@link #reset(long)} after consuming more than
   * {@code readLimit} bytes from this stream, unless startGrowingBuffer is
   * called first.
   */
  public long savePosition(int readLimit) {
    long offsetLimit = offset + readLimit;
    if (limit < offsetLimit) {
      setLimit(offsetLimit);
    }
    return offset;
  }

  /**
   * Tell this input stream to automatically allocate enough buffer to rewind
   * to {@code reset} as data is read from it. Useful if an acceptable {@code limit}
   * cannot be determined at compile time. Calling this method without
   * calling the stopGrowingBuffer() is not recommended.
   */
  public void startGrowingBuffer() {
    shouldGrowLimit = true;
  }

  /**
   * Tell this input stream that you no longer wish to automatically grow the
   * underlying buffer.
   */
  public void stopGrowingBuffer() {
    shouldGrowLimit = false;
  }

  /**
   * Makes sure that the underlying stream can backtrack the full range from
   * {@code reset} thru {@code limit}. Since we can't call {@code mark()}
   * without also adjusting the reset-to-position on the underlying stream this
   * method resets first and then marks the union of the two byte ranges. On
   * buffered streams this additional cursor motion shouldn't result in any
   * additional I/O.
   */
  private void setLimit(long limit) {
    try {
      if (reset < offset && offset <= this.limit) {
        in.reset();
        in.mark((int) (limit - reset));
        skip(reset, offset);
      } else {
        reset = offset;
        in.mark((int) (limit - offset));
      }
      this.limit = limit;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to mark: " + e);
    }
  }

  /**
   * Call with projected maximum read bytes to ensure the underlying stream
   * will still be able to backtrack to {@code reset} after a read or skip
   * operation happens
   */
  private void ensureBuffer(long bytes) {
    long newLimit = offset + bytes;
    if (newLimit > this.limit) {
      long bestLimit = Math.max(newLimit, this.reset + ((this.limit - this.reset) * 2));
      setLimit(bestLimit);
    }
  }

  /** Resets the stream to the most recent {@link #mark mark}. */
  @Override public void reset() throws IOException {
    reset(defaultMark);
  }

  /** Resets the stream to the position recorded by {@code token}. */
  public void reset(long token) throws IOException {
    if (offset > limit || token < reset) {
      throw new IOException("Cannot reset");
    }
    in.reset();
    skip(reset, token);
    offset = token;
  }

  /** Skips {@code target - current} bytes and returns. */
  private void skip(long current, long target) throws IOException {
    while (current < target) {
      long skipped = in.skip(target - current);
      if (skipped == 0) {
        if (read() == -1) {
          break; // EOF
        } else {
          skipped = 1;
        }
      }
      current += skipped;
    }
  }

  @Override public int read() throws IOException {

    if (shouldGrowLimit)
      ensureBuffer(1);

    int result = in.read();
    if (result != -1) {
      offset++;
    }
    return result;
  }

  @Override public int read(byte[] buffer) throws IOException {

    if (shouldGrowLimit)
      ensureBuffer(buffer.length);

    int count = in.read(buffer);
    if (count != -1) {
      offset += count;
    }
    return count;
  }

  @Override public int read(byte[] buffer, int offset, int length) throws IOException {
    if (shouldGrowLimit)
      ensureBuffer(length);

    int count = in.read(buffer, offset, length);
    if (count != -1) {
      this.offset += count;
    }
    return count;
  }

  @Override public long skip(long byteCount) throws IOException {
    if (shouldGrowLimit)
      ensureBuffer(byteCount);

    long skipped = in.skip(byteCount);
    offset += skipped;
    return skipped;
  }

  @Override public int available() throws IOException {
    return in.available();
  }

  @Override public void close() throws IOException {
    in.close();
  }

  @Override public boolean markSupported() {
    return in.markSupported();
  }
}
