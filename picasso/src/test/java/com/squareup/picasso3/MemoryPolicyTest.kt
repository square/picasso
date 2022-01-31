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