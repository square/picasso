/*
 * Copyright (C) 2022 Square, Inc.
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
import com.squareup.picasso3.MemoryPolicy.NO_CACHE
import com.squareup.picasso3.MemoryPolicy.NO_STORE
import org.junit.Test

class MemoryPolicyTest {

  @Test fun dontReadFromMemoryCache() {
    var memoryPolicy = 0
    memoryPolicy = memoryPolicy or NO_CACHE.index
    assertThat(MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)).isFalse()
  }

  @Test fun readFromMemoryCache() {
    var memoryPolicy = 0
    memoryPolicy = memoryPolicy or NO_STORE.index
    assertThat(MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)).isTrue()
  }

  @Test fun dontWriteToMemoryCache() {
    var memoryPolicy = 0
    memoryPolicy = memoryPolicy or NO_STORE.index
    assertThat(MemoryPolicy.shouldWriteToMemoryCache(memoryPolicy)).isFalse()
  }

  @Test fun writeToMemoryCache() {
    var memoryPolicy = 0
    memoryPolicy = memoryPolicy or NO_CACHE.index
    assertThat(MemoryPolicy.shouldWriteToMemoryCache(memoryPolicy)).isTrue()
  }
}
