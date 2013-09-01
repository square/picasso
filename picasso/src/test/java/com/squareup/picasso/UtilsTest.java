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
    Request request = new Request.Builder(URI_1).build();
    String key1 = createKey(request);
    String key2 = createKey(request);
    assertThat(key1).isEqualTo(key2);

    Transformation t1 = new TestTransformation("foo", null);
    Transformation t2 = new TestTransformation("foo", null);

    Request requestTransform1 = new Request.Builder(URI_1).transform(t1).build();
    Request requestTransform2 = new Request.Builder(URI_1).transform(t2).build();

    String single1 = createKey(requestTransform1);
    String single2 = createKey(requestTransform2);
    assertThat(single1).isEqualTo(single2);

    Transformation t3 = new TestTransformation("foo", null);
    Transformation t4 = new TestTransformation("bar", null);

    Request requestTransform3 = new Request.Builder(URI_1).transform(t3).transform(t4).build();
    Request requestTransform4 = new Request.Builder(URI_1).transform(t3).transform(t4).build();

    String double1 = createKey(requestTransform3);
    String double2 = createKey(requestTransform4);
    assertThat(double1).isEqualTo(double2);

    Transformation t5 = new TestTransformation("foo", null);
    Transformation t6 = new TestTransformation("bar", null);

    Request requestTransform5 = new Request.Builder(URI_1).transform(t5).transform(t6).build();
    Request requestTransform6 = new Request.Builder(URI_1).transform(t6).transform(t5).build();

    String order1 = createKey(requestTransform5);
    String order2 = createKey(requestTransform6);
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
