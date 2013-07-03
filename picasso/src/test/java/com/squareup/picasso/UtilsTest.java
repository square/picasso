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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.Utils.createKey;
import static com.squareup.picasso.Utils.parseResponseSourceHeader;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class) @Config(manifest = Config.NONE)
public class UtilsTest {

  @Test public void matchingRequestsHaveSameKey() {
    String key1 = createKey(URI_1, 0, null, null);
    String key2 = createKey(URI_1, 0, null, null);
    assertThat(key1).isEqualTo(key2);

    List<Transformation> t1 = new ArrayList<Transformation>();
    t1.add(new TestTransformation("foo", null));
    String single1 = createKey(URI_1, 0, null, t1);
    List<Transformation> t2 = new ArrayList<Transformation>();
    t2.add(new TestTransformation("foo", null));
    String single2 = createKey(URI_1, 0, null, t2);
    assertThat(single1).isEqualTo(single2);

    List<Transformation> t3 = new ArrayList<Transformation>();
    t3.add(new TestTransformation("foo", null));
    t3.add(new TestTransformation("bar", null));
    String double1 = createKey(URI_1, 0, null, t3);
    List<Transformation> t4 = new ArrayList<Transformation>();
    t4.add(new TestTransformation("foo", null));
    t4.add(new TestTransformation("bar", null));
    String double2 = createKey(URI_1, 0, null, t4);
    assertThat(double1).isEqualTo(double2);

    List<Transformation> t5 = new ArrayList<Transformation>();
    t5.add(new TestTransformation("foo", null));
    t5.add(new TestTransformation("bar", null));

    List<Transformation> t6 = new ArrayList<Transformation>();
    t6.add(new TestTransformation("bar", null));
    t6.add(new TestTransformation("foo", null));
    String order1 = createKey(URI_1, 0, null, t5);
    String order2 = createKey(URI_1, 0, null, t6);
    assertThat(order1).isNotEqualTo(order2);
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
