package com.squareup.picasso;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import org.fest.assertions.api.StringAssert;
import org.junit.Test;

import static org.fest.assertions.api.Assertions.assertThat;

@SuppressWarnings("ResultOfMethodCallIgnored") //
public class RewindInputStreamTest {
  @Test public void oneReadBuffered() throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(bytes("FOO"));
    RewindInputStream ris = new RewindInputStream(in, 10);
    byte[] buffer = new byte[3];

    ris.read(buffer);
    assertBytes(buffer).isEqualTo("FOO");

    ris.rewind();

    ris.read(buffer);
    assertBytes(buffer).isEqualTo("FOO");
  }

  @Test public void twoReadsBuffered() throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(bytes("FOOBAR"));
    RewindInputStream ris = new RewindInputStream(in, 10);
    byte[] buffer = new byte[3];

    ris.read(buffer);
    assertBytes(buffer).isEqualTo("FOO");
    ris.read(buffer);
    assertBytes(buffer).isEqualTo("BAR");

    ris.rewind();

    ris.read(buffer);
    assertBytes(buffer).isEqualTo("FOO");
    ris.read(buffer);
    assertBytes(buffer).isEqualTo("BAR");
  }

  @Test public void multipleBufferedReads() throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(bytes("FOOBARBAZFIZ"));
    RewindInputStream ris = new RewindInputStream(in, 20);
    byte[] buffer = new byte[3];

    ris.read(buffer);
    assertBytes(buffer).isEqualTo("FOO");
    ris.read(buffer);
    assertBytes(buffer).isEqualTo("BAR");

    ris.rewind();

    ris.read(buffer);
    assertBytes(buffer).isEqualTo("FOO");
    ris.read(buffer);
    assertBytes(buffer).isEqualTo("BAR");
    ris.read(buffer);
    assertBytes(buffer).isEqualTo("BAZ");
    ris.read(buffer);
    assertBytes(buffer).isEqualTo("FIZ");
  }

  @Test public void bufferedReadCrossingBoundary() throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(bytes("FOOBARBAZFIZ"));
    RewindInputStream ris = new RewindInputStream(in, 20);

    byte[] buffer1 = new byte[3];
    ris.read(buffer1);
    assertBytes(buffer1).isEqualTo("FOO");
    ris.read(buffer1);
    assertBytes(buffer1).isEqualTo("BAR");

    ris.rewind();

    byte[] buffer2 = new byte[4];
    ris.read(buffer2);
    assertBytes(buffer2).isEqualTo("FOOB");
    ris.read(buffer2);
    assertBytes(buffer2).isEqualTo("ARBA");
    ris.read(buffer2);
    assertBytes(buffer2).isEqualTo("ZFIZ");
  }

  private static byte[] bytes(String data) {
    return data.getBytes(Charset.forName("UTF-8"));
  }

  private static StringAssert assertBytes(byte[] bytes) {
    return assertThat(new String(bytes, Charset.forName("UTF-8")));
  }
}
