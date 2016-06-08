/*
 * Copyright (C) 2015 Square, Inc.
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
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.ANDROID.assertThat;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.entry;
import static org.fest.assertions.api.Assertions.fail;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ExifStreamReaderTest {
  @Test
  public void ExifReaderTest() throws IOException {
    for (int i=1;i<=8;i++) {
      InputStream is = getClass().getClassLoader().getResourceAsStream("Portrait_" + i + ".jpg");
      assertThat(ExifStreamReader.getOrientation(is)).isEqualTo(i);    
    }
  }

  @Test
  public void NonJpgExifReaderTest() throws IOException {
     InputStream is = getClass().getClassLoader().getResourceAsStream("Portrait_5.png");
     assertThat(ExifStreamReader.getOrientation(is)).isEqualTo(0);    
  }
}
