package com.squareup.picasso3;

import android.support.annotation.NonNull;
import java.io.IOException;
import java.io.InputStream;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SourceBufferingInputStreamTest {
  @Test public void replay() throws IOException {
    Buffer data = new Buffer().writeUtf8("Hello, world!");

    BufferedSource source = Okio.buffer(new OneByteSource(data));
    InputStream stream = new SourceBufferingInputStream(source);

    // Read a 5 byte header from the wrapping input stream.
    byte[] out = new byte[5];
    assertEquals(5, stream.read(out));
    assertEquals("Hello", new String(out, "UTF-8"));

    // Ensure we've only requested what we needed from the upstream.
    assertEquals(5, source.buffer().size());

    // Ensure we can read the entirety of the contents from the original source.
    assertEquals("Hello, world!", source.readUtf8());
  }

  @Test public void available() throws IOException {
    Buffer data = new Buffer().writeUtf8("Hello, world!");
    BufferedSource source = Okio.buffer((Source) data);
    InputStream stream = new SourceBufferingInputStream(source);
    assertEquals(0, stream.available());
    stream.read();
    // Assume the segment size is >=13 bytes.
    assertEquals(12, stream.available());

    Buffer buffer = new Buffer().writeUtf8("Hello, world!");
    stream = new SourceBufferingInputStream(buffer);
    assertEquals(13, stream.available());
  }

  /** Prevents a consumer from reading large chunks and exercises edge cases. */
  private static final class OneByteSource implements Source {
    private final Source upstream;

    OneByteSource(Source upstream) {
      this.upstream = upstream;
    }

    @Override public long read(@NonNull Buffer sink, long byteCount) throws IOException {
      if (byteCount > 0) {
        return upstream.read(sink, 1);
      }
      return 0;
    }

    @Override public Timeout timeout() {
      return upstream.timeout();
    }

    @Override public void close() throws IOException {
      upstream.close();
    }
  }
}
