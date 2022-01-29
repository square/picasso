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
package com.squareup.picasso3;

import android.content.res.Resources;
import java.io.IOException;
import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso3.TestUtils.RESOURCE_ID_1;
import static com.squareup.picasso3.TestUtils.RESOURCE_ID_URI;
import static com.squareup.picasso3.TestUtils.RESOURCE_TYPE_URI;
import static com.squareup.picasso3.TestUtils.URI_1;
import static com.squareup.picasso3.TestUtils.mockPackageResourceContext;
import static com.squareup.picasso3.Utils.isWebPFile;

@RunWith(RobolectricTestRunner.class)
public class UtilsTest {

  @Test public void matchingRequestsHaveSameKey() {
    Request request = new Request.Builder(URI_1).build();
    Request request2 = new Request.Builder(URI_1).build();
    assertThat(request.key).isEqualTo(request2.key);

    Transformation t1 = new TestTransformation("foo", null);
    Transformation t2 = new TestTransformation("foo", null);
    Request requestTransform1 = new Request.Builder(URI_1).transform(t1).build();
    Request requestTransform2 = new Request.Builder(URI_1).transform(t2).build();
    assertThat(requestTransform1.key).isEqualTo(requestTransform2.key);

    Transformation t3 = new TestTransformation("foo", null);
    Transformation t4 = new TestTransformation("bar", null);
    Request requestTransform3 = new Request.Builder(URI_1).transform(t3).transform(t4).build();
    Request requestTransform4 = new Request.Builder(URI_1).transform(t3).transform(t4).build();
    assertThat(requestTransform3.key).isEqualTo(requestTransform4.key);

    Transformation t5 = new TestTransformation("foo", null);
    Transformation t6 = new TestTransformation("bar", null);
    Request requestTransform5 = new Request.Builder(URI_1).transform(t5).transform(t6).build();
    Request requestTransform6 = new Request.Builder(URI_1).transform(t6).transform(t5).build();
    assertThat(requestTransform5.key).isNotEqualTo(requestTransform6.key);
  }

  @Test public void detectedWebPFile() throws Exception {
    assertThat(isWebPFile(new Buffer().writeUtf8("RIFFxxxxWEBP"))).isTrue();
    assertThat(isWebPFile(new Buffer().writeUtf8("RIFFxxxxxWEBP"))).isFalse();
    assertThat(isWebPFile(new Buffer().writeUtf8("ABCDxxxxWEBP"))).isFalse();
    assertThat(isWebPFile(new Buffer().writeUtf8("RIFFxxxxABCD"))).isFalse();
    assertThat(isWebPFile(new Buffer().writeUtf8("RIFFxxWEBP"))).isFalse();
  }

  @Test public void ensureBuilderIsCleared() {
    new Request.Builder(RESOURCE_ID_URI).build();
    assertThat(Utils.MAIN_THREAD_KEY_BUILDER.length()).isEqualTo(0);
    new Request.Builder(URI_1).build();
    assertThat(Utils.MAIN_THREAD_KEY_BUILDER.length()).isEqualTo(0);
  }

  @Test public void getResourceById() throws IOException {
    Request request = new Request.Builder(RESOURCE_ID_URI).build();
    Resources res = Utils.getResources(mockPackageResourceContext(), request);
    int id = Utils.getResourceId(res, request);
    assertThat(id).isEqualTo(RESOURCE_ID_1);
  }

  @Test public void getResourceByTypeAndName() throws IOException {
    Request request = new Request.Builder(RESOURCE_TYPE_URI).build();
    Resources res = Utils.getResources(mockPackageResourceContext(), request);
    int id = Utils.getResourceId(res, request);
    assertThat(id).isEqualTo(RESOURCE_ID_1);
  }
}
