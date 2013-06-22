package com.squareup.picasso;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link InputStream} wrapper which buffers all reads until {@link #rewind()} is called at
 * which point it replays the data from the beginning. The stream also supports {@link #mark(int)}
 * and {@link #reset()} independently of the rewind buffer which is used extensively during bitmap
 * decoding for format detection and skipping metadata.
 */
final class BitmapInputStream extends InputStream {
  private static final int DEFAULT_BUFFER_SIZE = 4096;
  private static final boolean DEBUG = false;
  private static final String TAG = "BitmapInputStream";

  /** A {@link ByteArrayOutputStream} which directly returns the underlying buffer. */
  private static class CopyFreeByteArrayOutputStream extends ByteArrayOutputStream {
    public CopyFreeByteArrayOutputStream(int bufferSize) {
      super(bufferSize);
    }

    @Override public byte[] toByteArray() {
      return buf;
    }
  }

  private ByteArrayOutputStream buffer;
  private InputStream original;

  private byte[] skipBuffer;
  private byte[] lameBuffer = new byte[1];

  private boolean isReplaying;

  private int streamPos;
  private int readPos;
  private int markPos;

  BitmapInputStream(InputStream original) {
    this.original = original;
    buffer = new CopyFreeByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
  }

  /**
   * Replay any buffered data on subsequent reads until exhausted and then fall back to the
   * original stream.
   */
  public void rewind() {
    if (isReplaying) {
      throw new IllegalStateException("Rewind may only be called once.");
    }
    isReplaying = true;
    readPos = 0;

    if (DEBUG) log("rewind()");
  }

  @Override public int read() throws IOException {
    if (read(lameBuffer, 0, 1) != 1) {
      return -1;
    }
    return lameBuffer[0];
  }

  @Override public int read(byte[] b, int offset, int length) throws IOException {
    if (DEBUG) log("read(byte[%s], offset=%s, length=%s)", b.length, offset, length);

    ByteArrayOutputStream buffer = this.buffer;
    // Number of bytes the rewind buffer still has.
    int have = Math.max(buffer.size() - readPos, 0);
    // Number of bytes the wanted starting at the offset.
    int want = length - offset;
    if (DEBUG) log("  Read wants %s bytes, buffer has %s", want, have);

    int given = 0;
    if (have > 0) {
      // Ensure we don't copy more than the buffer wants.
      have = Math.min(have, want);

      System.arraycopy(buffer.toByteArray(), readPos, b, offset, have);
      if (DEBUG) log("  BUFFER reading %s at position %s, got %s", have, readPos, have);

      // Update the read position, bytes wanted, and bytes given based on what we read.
      readPos += have;
      want -= have;
      given += have;

      // Adjust the view into the output buffer in case we try to fill from the stream as well.
      offset += have;
      length -= have;
    }

    // If the buffer didn't have enough bytes or was empty, read from the stream.
    if (want > 0) {
      given += original.read(b, offset, length);
      int got = given - have;
      streamPos += got;
      readPos += got;
      if (DEBUG) log("  STREAM reading %s at offset %s, got %s", length, offset, got);

      if (!isReplaying) {
        buffer.write(b, offset, got);
        if (DEBUG) log("  BUFFER writing %s", got);
      }
    }

    return given;
  }

  @Override public long skip(long count) throws IOException {
    if (DEBUG) log("skip(count=%s)", count);

    ByteArrayOutputStream buffer = this.buffer;

    if (isReplaying) {
      long have = buffer.size() - readPos;
      if (have > 0) {
        // Ensure we don't skip more than the buffer has.
        long skip = Math.min(have, count);

        // Skipping in the buffer just means moving the pointer.
        readPos += skip;

        if (DEBUG) log("  BUFFER skipped %s", skip);
        return skip;
      }

      // No buffered data left, skip in the real stream.
      long skipped = original.skip(count);
      readPos += skipped;
      streamPos += skipped;
      if (DEBUG) log("  STREAM skipped %s", skipped);
      return skipped;
    }

    if (skipBuffer == null) {
      skipBuffer = new byte[1024]; // Number chosen by exact science.
    }

    // Ensure we don't skip more than the skip buffer can hold.
    int skip = Math.min(skipBuffer.length, (int) count);

    // Try to read the amount we are skipping and write it to the buffer.
    int skipped = original.read(skipBuffer, 0, skip);
    buffer.write(skipBuffer, 0, skipped);

    readPos += skipped;
    streamPos += skipped;
    if (DEBUG) log("  STREAM skipped to buffer %s bytes", skipped);

    return skipped;
  }

  @Override public int available() throws IOException {
    int available = original.available();
    if (isReplaying) {
      available += buffer.size() - readPos;
    }
    if (DEBUG) log("available() = %s", available);
    return available;
  }

  @Override public void close() throws IOException {
    if (DEBUG) log("close()");
    InputStream original = this.original;
    this.original = null;
    skipBuffer = null;
    buffer = null;
    original.close();
  }

  /**
   * This version of mark ignores the argument value since we need to buffer everything
   * <p/>
   * {@inheritDoc}
   */
  @Override public synchronized void mark(int readLimit) {
    markPos = readPos;
    if (DEBUG) log("mark(readLimit=%s) markPos=%s", readLimit, markPos);
  }

  @Override public synchronized void reset() throws IOException {
    if (DEBUG) log("reset()");
    if (markPos == -1) {
      throw new IOException("Mark has been invalidated.");
    }
    readPos = markPos;
    if (DEBUG) log("  readPos is now %s", readPos);
  }

  @Override public boolean markSupported() {
    return true;
  }

  private void log(String message, Object... args) {
    Log.d(TAG, String.format(message, args));
    Log.d(TAG,
        String.format("    streamPos: %s, readPos: %s, markPos: %s, bufferSize: %s", streamPos,
            readPos, markPos, buffer.size()));
  }
}
