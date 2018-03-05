/*
 * Copyright (C) 2018 Square, Inc.
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

import java.io.IOException;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

/**
 * Builds a buffered source that can rewind to a marked position earlier in the stream.
 *
 * <p>Mark potential positions to rewind back to with {@link #mark}; rewind back to these positions
 * with {@link #reset}. Both operations apply to the position in the {@linkplain #source() buffered
 * source}; resetting will impact the buffer.
 *
 * <p>When marking it is necessary to specify how much data to retain. Once you advance above this
 * limit, the mark is discarded and resetting is not permitted. This may be used to lookahead a
 * fixed number of bytes without loading an entire stream into memory. To reset an arbitrary
 * number of bytes use {@code mark(Long#MAX_VALUE)}.
 */
public final class SourceMarker {

  /*
   * This class wraps the underlying source in a MarkSource to support mark and reset. It creates a
   * BufferedSource for the caller so that it can track its offsets and manipulate its buffer.
   */

  /**
   * The offset into the underlying source. To compute the user's offset start with this and
   * subtract userBuffer.size().
   */
  long offset;

  /** The offset of the earliest mark, or -1 for no mark. */
  long mark = -1L;

  /** The offset of the latest readLimit, or -1 for no mark. */
  long limit = -1L;

  boolean closed;

  final MarkSource markSource;
  final BufferedSource userSource;

  /** A copy of the underlying source's data beginning at {@code mark}. */
  final Buffer markBuffer;

  /** Just the userSource's buffer. */
  final Buffer userBuffer;

  public SourceMarker(Source source) {
    this.markSource = new MarkSource(source);
    this.markBuffer = new Buffer();
    this.userSource = Okio.buffer(markSource);
    this.userBuffer = userSource.buffer();
  }

  public BufferedSource source() {
    return userSource;
  }

  /**
   * Marks the current position in the stream as one to potentially return back to. Returns the
   * offset of this position. Call {@link #reset(long)} with this position to return to it later. It
   * is an error to call {@link #reset(long)} after consuming more than {@code readLimit} bytes from
   * {@linkplain #source() the source}.
   */
  public long mark(long readLimit) throws IOException {
    if (readLimit < 0L) {
      throw new IllegalArgumentException("readLimit < 0: " + readLimit);
    }

    if (closed) {
      throw new IllegalStateException("closed");
    }

    // Mark the current position in the buffered source.
    long userOffset = offset - userBuffer.size();

    // If this is a new mark promote userBuffer data into the markBuffer.
    if (mark == -1L) {
      markBuffer.writeAll(userBuffer);
      mark = userOffset;
      offset = userOffset;
    }

    // Grow the limit if necessary.
    long newMarkBufferLimit = userOffset + readLimit;
    if (newMarkBufferLimit < 0) newMarkBufferLimit = Long.MAX_VALUE; // Long overflow!
    limit = Math.max(limit, newMarkBufferLimit);

    return userOffset;
  }

  /** Resets {@linkplain #source() the source} to {@code userOffset}. */
  public void reset(long userOffset) throws IOException {
    if (closed) {
      throw new IllegalStateException("closed");
    }

    if (userOffset < mark // userOffset is before mark.
        || userOffset > limit // userOffset is beyond limit.
        || userOffset > mark + markBuffer.size() // userOffset is in the future.
        || offset - userBuffer.size() > limit) { // Stream advanced beyond limit.
      throw new IOException("cannot reset to " + userOffset + ": out of range");
    }

    // Clear userBuffer to cause data at 'offset' to be returned by the next read.
    offset = userOffset;
    userBuffer.clear();
  }

  final class MarkSource extends ForwardingSource {
    MarkSource(Source source) {
      super(source);
    }

    @Override public long read(Buffer sink, long byteCount) throws IOException {
      if (closed) {
        throw new IllegalStateException("closed");
      }

      // If there's no mark, go to the underlying source.
      if (mark == -1L) {
        long result = super.read(sink, byteCount);
        if (result == -1L) return -1L;
        offset += result;
        return result;
      }

      // If we can read from markBuffer, do that.
      if (offset < mark + markBuffer.size()) {
        long posInBuffer = offset - mark;
        long result = Math.min(byteCount, markBuffer.size() - posInBuffer);
        markBuffer.copyTo(sink, posInBuffer, result);
        offset += result;
        return result;
      }

      // If we can write to markBuffer, do that.
      if (offset < limit) {
        long byteCountBeforeLimit = limit - (mark + markBuffer.size());
        long result = super.read(markBuffer, Math.min(byteCount, byteCountBeforeLimit));
        if (result == -1L) return -1L;
        markBuffer.copyTo(sink, markBuffer.size() - result, result);
        offset += result;
        return result;
      }

      // Attempt to read past the limit. Data will not be saved.
      long result = super.read(sink, byteCount);
      if (result == -1L) return -1L;

      // We read past the limit. Discard marked data.
      markBuffer.clear();
      mark = -1L;
      limit = -1L;
      return result;
    }

    @Override public void close() throws IOException {
      if (closed) return;

      closed = true;
      markBuffer.clear();
      super.close();
    }
  }
}
