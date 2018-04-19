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
package com.squareup.picasso3;

import android.support.annotation.NonNull;
import java.io.IOException;
import java.io.InputStream;
import okio.Buffer;
import okio.BufferedSource;

/**
 * An {@link InputStream} that fills the buffer of an {@link BufferedSource} as reads are requested
 * and copies its bytes into the byte arrays of the caller. This allows you to read as much of the
 * underlying {@link BufferedSource} as you want through the eyes of this {@link InputStream} while
 * still preserving the ability to still consume the {@link BufferedSource} once you are done with
 * this instance.
 */
final class SourceBufferingInputStream extends InputStream {
  private final BufferedSource source;
  private final Buffer buffer;
  private long position;
  private long markPosition = -1;
  private long markLimit = -1;

  SourceBufferingInputStream(BufferedSource source) {
    this.source = source;
    this.buffer = source.buffer();
  }

  private final Buffer temp = new Buffer();
  private int copyTo(byte[] sink, int offset, int byteCount) {
    // TODO replace this with https://github.com/square/okio/issues/362
    // `copyTo` treats offset as the read position, `read` treats offset as the write offset.
    buffer.copyTo(temp, position, byteCount);
    return temp.read(sink, offset, byteCount);
  }

  @Override public int read() throws IOException {
    if (!source.request(position + 1)) {
      return -1;
    }
    byte value = buffer.getByte(position++);
    if (position > markLimit) {
      markPosition = -1;
    }
    return value;
  }

  @Override public int read(@NonNull byte[] b, int off, int len) throws IOException {
    if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return 0;
    }

    int count = len;
    if (!source.request(position + count)) {
      count = available();
    }
    if (count == 0) return -1;

    int copied = /*buffer.*/copyTo(b, off, count);
    position += copied;
    if (position > markLimit) {
      markPosition = -1;
    }
    return copied;
  }

  @Override public long skip(long n) throws IOException {
    source.require(position + n);
    position += n;
    if (position > markLimit) {
      markPosition = -1;
    }
    return n;
  }

  @Override public boolean markSupported() {
    return true;
  }

  @Override public void mark(int readlimit) {
    markPosition = position;
    markLimit = position + readlimit;
  }

  @Override public void reset() throws IOException {
    if (markPosition == -1) {
      throw new IOException("No mark or mark expired");
    }
    position = markPosition;
    markPosition = -1;
    markLimit = -1;
  }

  @Override public int available() {
    return (int) Math.min(buffer.size() - position, Integer.MAX_VALUE);
  }

  @Override public void close() {
  }
}
