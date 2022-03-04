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
package com.squareup.picasso3

import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.TestUtils.RESOURCE_ID_1
import com.squareup.picasso3.TestUtils.RESOURCE_ID_URI
import com.squareup.picasso3.TestUtils.RESOURCE_TYPE_URI
import com.squareup.picasso3.TestUtils.URI_1
import com.squareup.picasso3.TestUtils.mockPackageResourceContext
import com.squareup.picasso3.Utils.isWebPFile
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UtilsTest {
  @Test fun matchingRequestsHaveSameKey() {
    val request = Request.Builder(URI_1).build()
    val request2 = Request.Builder(URI_1).build()
    assertThat(request.key).isEqualTo(request2.key)

    val t1 = TestTransformation("foo", null)
    val t2 = TestTransformation("foo", null)
    val requestTransform1 = Request.Builder(URI_1).transform(t1).build()
    val requestTransform2 = Request.Builder(URI_1).transform(t2).build()
    assertThat(requestTransform1.key).isEqualTo(requestTransform2.key)

    val t3 = TestTransformation("foo", null)
    val t4 = TestTransformation("bar", null)
    val requestTransform3 = Request.Builder(URI_1).transform(t3).transform(t4).build()
    val requestTransform4 = Request.Builder(URI_1).transform(t3).transform(t4).build()
    assertThat(requestTransform3.key).isEqualTo(requestTransform4.key)

    val t5 = TestTransformation("foo", null)
    val t6 = TestTransformation("bar", null)
    val requestTransform5 = Request.Builder(URI_1).transform(t5).transform(t6).build()
    val requestTransform6 = Request.Builder(URI_1).transform(t6).transform(t5).build()
    assertThat(requestTransform5.key).isNotEqualTo(requestTransform6.key)
  }

  @Test fun detectedWebPFile() {
    assertThat(isWebPFile(Buffer().writeUtf8("RIFFxxxxWEBP"))).isTrue()
    assertThat(isWebPFile(Buffer().writeUtf8("RIFFxxxxxWEBP"))).isFalse()
    assertThat(isWebPFile(Buffer().writeUtf8("ABCDxxxxWEBP"))).isFalse()
    assertThat(isWebPFile(Buffer().writeUtf8("RIFFxxxxABCD"))).isFalse()
    assertThat(isWebPFile(Buffer().writeUtf8("RIFFxxWEBP"))).isFalse()
  }

  @Test fun ensureBuilderIsCleared() {
    Request.Builder(RESOURCE_ID_URI).build()
    assertThat(Utils.MAIN_THREAD_KEY_BUILDER.length).isEqualTo(0)
    Request.Builder(URI_1).build()
    assertThat(Utils.MAIN_THREAD_KEY_BUILDER.length).isEqualTo(0)
  }

  @Test fun getResourceById() {
    val request = Request.Builder(RESOURCE_ID_URI).build()
    val res = Utils.getResources(mockPackageResourceContext(), request)
    val id = Utils.getResourceId(res, request)
    assertThat(id).isEqualTo(RESOURCE_ID_1)
  }

  @Test fun getResourceByTypeAndName() {
    val request = Request.Builder(RESOURCE_TYPE_URI).build()
    val res = Utils.getResources(mockPackageResourceContext(), request)
    val id = Utils.getResourceId(res, request)
    assertThat(id).isEqualTo(RESOURCE_ID_1)
  }
}
