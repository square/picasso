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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class MarkableInputStreamTest {
@Test
  public void test() throws Exception {
    MarkableInputStream in = new MarkableInputStream(new ByteArrayInputStream(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(Charset.forName("US-ASCII"))));
    assertThat(readBytes(in, 3)).isEqualTo("ABC");
    long posA = in.savePosition(7);// DEFGHIJ
    assertThat(readBytes(in, 4)).isEqualTo("DEFG");
    in.mark(5); // HIJKL
    assertThat(readBytes(in, 4)).isEqualTo("HIJK");
    in.reset(); // Back to 'H'
    assertThat(readBytes(in, 3)).isEqualTo("HIJ");
    in.reset(posA); // Back to 'D'
    assertThat(readBytes(in, 7)).isEqualTo("DEFGHIJ");
    in.reset(); // Back to 'H' again.
    assertThat(readBytes(in, 6)).isEqualTo("HIJKLM");
    try {
      in.reset();
      fail();
    } catch (IOException expected) {
    }
    try {
      in.reset(posA);
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void exceedLimitTest() throws Exception {
    MarkableInputStream in = new MarkableInputStream(new ByteArrayInputStream(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(Charset.forName("US-ASCII"))));
    in.allowMarksToExpire(false);
    assertThat(readBytes(in, 3)).isEqualTo("ABC");
    long posA = in.savePosition(7);// DEFGHIJ
    assertThat(readBytes(in, 4)).isEqualTo("DEFG");
    in.mark(5); // HIJKL
    assertThat(readBytes(in, 4)).isEqualTo("HIJK");
    in.reset(); // Back to 'H'
    assertThat(readBytes(in, 3)).isEqualTo("HIJ");
    in.reset(posA); // Back to 'D'
    assertThat(readBytes(in, 7)).isEqualTo("DEFGHIJ");
    in.reset(); // Back to 'H' again.
    assertThat(readBytes(in, 6)).isEqualTo("HIJKLM");
    
    in.reset(); // Back to 'H' again despite the original limit being exceeded
    assertThat(readBytes(in, 2)).isEqualTo("HI"); // confirm we're really back at H

    in.reset(posA); // Back to 'D' again despite the original limit being exceeded
    assertThat(readBytes(in, 2)).isEqualTo("DE"); // confirm that we're really back at D  
  }

  private String readBytes(InputStream in, int count) throws IOException {
    byte[] result = new byte[count];
    assertThat(in.read(result)).isEqualTo(count);
    return new String(result, "US-ASCII");
  }
}
