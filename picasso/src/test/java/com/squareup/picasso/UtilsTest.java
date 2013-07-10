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

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Utils.createKey;
import static com.squareup.picasso.Utils.parseResponseSourceHeader;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class UtilsTest {
  private static final Uri URL = Uri.parse("http://example.com/a.png");

  private Picasso picasso = mock(Picasso.class);

  @Test public void matchingRequestsHaveSameKey() {
    Request r1 = new Request(picasso, URL, 0, null, null, null, false, false, 0, null, false);
    Request r2 = new Request(picasso, URL, 0, null, null, null, false, false, 0, null, false);
    assertThat(createKey(r1)).isEqualTo(createKey(r2));

    List<Transformation> t1 = new ArrayList<Transformation>();
    t1.add(new TestTransformation("foo", null));
    Request single1 = new Request(picasso, URL, 0, null, null, t1, false, false, 0, null, false);
    List<Transformation> t2 = new ArrayList<Transformation>();
    t2.add(new TestTransformation("foo", null));
    Request single2 = new Request(picasso, URL, 0, null, null, t2, false, false, 0, null, false);
    assertThat(createKey(single1)).isEqualTo(createKey(single2));

    List<Transformation> t3 = new ArrayList<Transformation>();
    t3.add(new TestTransformation("foo", null));
    t3.add(new TestTransformation("bar", null));
    Request double1 = new Request(picasso, URL, 0, null, null, t3, false, false, 0, null, false);
    List<Transformation> t4 = new ArrayList<Transformation>();
    t4.add(new TestTransformation("foo", null));
    t4.add(new TestTransformation("bar", null));
    Request double2 = new Request(picasso, URL, 0, null, null, t4, false, false, 0, null, false);
    assertThat(createKey(double1)).isEqualTo(createKey(double2));

    List<Transformation> t5 = new ArrayList<Transformation>();
    t5.add(new TestTransformation("foo", null));
    t5.add(new TestTransformation("bar", null));

    List<Transformation> t6 = new ArrayList<Transformation>();
    t6.add(new TestTransformation("bar", null));
    t6.add(new TestTransformation("foo", null));
    Request order1 = new Request(picasso, URL, 0, null, null, t5, false, false, 0, null, false);
    Request order2 = new Request(picasso, URL, 0, null, null, t6, false, false, 0, null, false);
    assertThat(createKey(order1)).isNotEqualTo(createKey(order2));
  }

  @Test public void loadedFromCache() {
    assertThat(parseResponseSourceHeader(null)).isFalse();
    assertThat(parseResponseSourceHeader("CACHE 200")).isTrue();
    assertThat(parseResponseSourceHeader("STREAM 200")).isFalse();
    assertThat(parseResponseSourceHeader("CONDITIONAL_CACHE 200")).isFalse();
    assertThat(parseResponseSourceHeader("CONDITIONAL_CACHE 304")).isTrue();
    assertThat(parseResponseSourceHeader("STREAM 304")).isFalse();
    assertThat(parseResponseSourceHeader("")).isFalse();
    assertThat(parseResponseSourceHeader("HELLO WORLD")).isFalse();
  }
}
