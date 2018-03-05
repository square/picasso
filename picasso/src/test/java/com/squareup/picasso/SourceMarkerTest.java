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

import java.io.IOException;
import java.util.Arrays;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public final class SourceMarkerTest {
  @Test public void test() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    assertThat(source.readUtf8(3)).isEqualTo("ABC");
    long pos3 = marker.mark(7); // DEFGHIJ
    assertThat(source.readUtf8(4)).isEqualTo("DEFG");
    long pos7 = marker.mark(5); // HIJKL
    assertThat(source.readUtf8(4)).isEqualTo("HIJK");
    marker.reset(pos7); // Back to 'H'
    assertThat(source.readUtf8(3)).isEqualTo("HIJ");
    marker.reset(pos3); // Back to 'D'
    assertThat(source.readUtf8(7)).isEqualTo("DEFGHIJ");
    marker.reset(pos7); // Back to 'H' again.
    assertThat(source.readUtf8(6)).isEqualTo("HIJKLM");
    try {
      marker.reset(pos7);
      fail();
    } catch (IOException expected) {
      assertThat(expected).hasMessageThat()
          .isEqualTo("cannot reset to 7: out of range");
    }
    try {
      marker.reset(pos3);
      fail();
    } catch (IOException expected) {
      assertThat(expected).hasMessageThat()
          .isEqualTo("cannot reset to 3: out of range");
    }
  }

  @Test public void exceedLimitTest() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    assertThat(source.readUtf8(3)).isEqualTo("ABC");
    long pos3 = marker.mark(Long.MAX_VALUE); // DEFGHIJ
    assertThat(source.readUtf8(4)).isEqualTo("DEFG");
    long pos7 = marker.mark(5); // HIJKL
    assertThat(source.readUtf8(4)).isEqualTo("HIJK");
    marker.reset(pos7); // Back to 'H'
    assertThat(source.readUtf8(3)).isEqualTo("HIJ");
    marker.reset(pos3); // Back to 'D'
    assertThat(source.readUtf8(7)).isEqualTo("DEFGHIJ");
    marker.reset(pos7); // Back to 'H' again.
    assertThat(source.readUtf8(6)).isEqualTo("HIJKLM");

    marker.reset(pos7); // Back to 'H' again despite the original limit being exceeded
    assertThat(source.readUtf8(2)).isEqualTo("HI"); // confirm we're really back at H

    marker.reset(pos3); // Back to 'D' again despite the original limit being exceeded
    assertThat(source.readUtf8(2)).isEqualTo("DE"); // confirm that we're really back at D
  }

  @Test public void markAndLimitSmallerThanUserBuffer() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    // Load 5 bytes into the user buffer, then mark 0..3 and confirm that resetting from 4 fails.
    source.require(5);
    long pos0 = marker.mark(3);
    assertThat(source.readUtf8(3)).isEqualTo("ABC");
    marker.reset(pos0);
    assertThat(source.readUtf8(4)).isEqualTo("ABCD");
    try {
      marker.reset(pos0);
      fail();
    } catch (IOException expected) {
      assertThat(expected).hasMessageThat()
          .isEqualTo("cannot reset to 0: out of range");
    }
  }

  @Test public void resetTooLow() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    source.skip(3);
    marker.mark(3);
    source.skip(2);
    try {
      marker.reset(2);
      fail();
    } catch (IOException expected) {
      assertThat(expected).hasMessageThat()
          .isEqualTo("cannot reset to 2: out of range");
    }
  }

  @Test public void resetTooHigh() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    marker.mark(3);
    source.skip(6);
    try {
      marker.reset(4);
      fail();
    } catch (IOException expected) {
      assertThat(expected).hasMessageThat()
          .isEqualTo("cannot reset to 4: out of range");
    }
  }

  @Test public void resetUnread() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    marker.mark(3);
    try {
      marker.reset(2);
      fail();
    } catch (IOException expected) {
      assertThat(expected).hasMessageThat()
          .isEqualTo("cannot reset to 2: out of range");
    }
  }

  @Test public void markNothingBuffered() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    long pos0 = marker.mark(5);
    assertThat(source.readUtf8(4)).isEqualTo("ABCD");
    marker.reset(pos0);
    assertThat(source.readUtf8(6)).isEqualTo("ABCDEF");
  }

  @Test public void mark0() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    long pos0 = marker.mark(0);
    marker.reset(pos0);
    assertThat(source.readUtf8(3)).isEqualTo("ABC");
  }

  @Test public void markNegative() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    try {
      marker.mark(-1L);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("readLimit < 0: -1");
    }
  }

  @Test public void resetAfterEof() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDE"));
    BufferedSource source = marker.source();

    long pos0 = marker.mark(5);
    assertThat(source.readUtf8()).isEqualTo("ABCDE");
    marker.reset(pos0);
    assertThat(source.readUtf8(3)).isEqualTo("ABC");
  }

  @Test public void closeThenMark() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    source.close();
    try {
      marker.mark(5);
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("closed");
    }
  }

  @Test public void closeThenReset() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    long pos0 = marker.mark(5);
    source.close();
    try {
      marker.reset(pos0);
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("closed");
    }
  }

  @Test public void closeThenRead() throws Exception {
    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    BufferedSource source = marker.source();

    source.close();
    try {
      source.readUtf8(3);
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("closed");
    }
  }

  @Test public void multipleSegments() throws Exception {
    String as = repeat('a', 10_000);
    String bs = repeat('b', 10_000);
    String cs = repeat('c', 10_000);
    String ds = repeat('d', 10_000);

    SourceMarker marker = new SourceMarker(new Buffer().writeUtf8(as + bs + cs + ds));
    BufferedSource source = marker.source();

    assertThat(source.readUtf8(10_000)).isEqualTo(as);
    long pos10k = marker.mark(15_000);
    assertThat(source.readUtf8(10_000)).isEqualTo(bs);
    long pos20k = marker.mark(15_000);
    assertThat(source.readUtf8(10_000)).isEqualTo(cs);
    marker.reset(pos20k);
    marker.reset(pos10k);
    assertThat(source.readUtf8(30_000)).isEqualTo(bs + cs + ds);
  }

  private String repeat(char c, int count) {
    char[] array = new char[count];
    Arrays.fill(array, c);
    return new String(array);
  }
}
