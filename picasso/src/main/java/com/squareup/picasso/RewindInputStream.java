package com.squareup.picasso;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jetbrains.annotations.TestOnly;

/**
 * An {@link InputStream} wrapper which buffers all reads until {@link #rewind()} is called at
 * which point it replays the data from the beginning.
 */
final class RewindInputStream extends InputStream {
  private static final int DEFAULT_BUFFER_SIZE = 4096;
  private static final boolean DEBUG = false;
  private static final String TAG = "RewindInputStream";

  /** A {@link ByteArrayOutputStream} which directly returns the underlying buffer. */
  private static class CopyFreeByteArrayOutputStream extends ByteArrayOutputStream {
    public CopyFreeByteArrayOutputStream(int bufferSize) {
      super(bufferSize);
    }

    @Override public byte[] toByteArray() {
      return buf;
    }
  }

  private final ByteArrayOutputStream buffer;
  private final InputStream original;
  private boolean replaying;
  byte[] bufferData;
  int bufferLength;
  int bufferPosition;

  public RewindInputStream(InputStream original) {
    this(original, DEFAULT_BUFFER_SIZE);
  }

  @TestOnly RewindInputStream(InputStream original, int bufferSize) {
    this.original = original;
    this.buffer = new CopyFreeByteArrayOutputStream(bufferSize);
  }

  /**
   * Replay any buffered data on subsequent reads until exhausted and then fall back to the
   * original stream.
   */
  public void rewind() {
    if (replaying) {
      throw new IllegalStateException("Rewind may only be called once.");
    }
    if (DEBUG) log("> rewind()");

    replaying = true;
    bufferData = buffer.toByteArray();
    bufferLength = buffer.size();
    bufferPosition = 0;

    if (DEBUG) log("  bufferLength: %s", bufferLength);
  }

  @Override public int read() throws IOException {
    final int bits;
    if (replaying) {
      if (bufferPosition < bufferLength) {
        bits = bufferData[bufferPosition++];
      } else {
        bits = original.read();
      }
    } else {
      bits = original.read();
      buffer.write(bits);
    }
    return bits;
  }

  @Override public int read(byte[] b, int offset, int length) throws IOException {
    if (DEBUG) log("> read(byte[], offset=%s, length=%s)", offset, length);
    int given = 0;

    if (replaying) {
      // Number of bytes the buffer wants starting at offset.
      int want = length - offset;
      // Number of bytes the rewind buffer still has.
      int have = bufferLength - bufferPosition;

      if (DEBUG) log("  want=%s, have=%s", want, have);

      if (have > 0) {
        // Ensure we don't copy more than the buffer wants.
        have = Math.min(have, want);

        System.arraycopy(bufferData, bufferPosition, b, offset, have);
        if (DEBUG) log("  Buffer at %s into %s for %s, got %s", bufferPosition, offset, have, have);

        bufferPosition += have;
        want -= have;
        given += have;
        if (DEBUG) log("    New: bufferPosition=%s, want=%s", bufferPosition, want);

        // Adjust the view into the output buffer in case we try to fill from the original as well.
        offset += have;
        length -= have;
        if (DEBUG) log("    New: offset=%s, length=%s", offset, length);
      }

      if (want > 0) {
        given += original.read(b, offset, length);
        if (DEBUG) log("  Real into %s for %s, got=%s", offset, length, (given - have));
      }
    } else {
      given = original.read(b, offset, length);
      if (DEBUG) log("  Real into %s for %s, got=%s", offset, length, given);

      buffer.write(b, offset, given);
      if (DEBUG) log("  Buffer write of %s to size=%s", (given - offset), buffer.size());
    }

    return given;
  }

  @Override public long skip(long count) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public int available() throws IOException {
    int available = original.available();
    if (replaying) {
      available += (bufferLength - bufferPosition);
    }
    return available;
  }

  @Override public void close() throws IOException {
    original.close();
  }

  @Override public synchronized void mark(int readLimit) {
    throw new UnsupportedOperationException();
  }

  @Override public synchronized void reset() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public boolean markSupported() {
    return false;
  }

  private void log(String message, Object... args) {
    Log.d(TAG, "{" + Integer.toHexString(hashCode()) + "} " + String.format(message, args));
  }
}
