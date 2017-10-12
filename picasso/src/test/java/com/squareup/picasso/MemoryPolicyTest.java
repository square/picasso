package com.squareup.picasso;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class MemoryPolicyTest {

  @Test public void dontReadFromMemoryCache() {
    int memoryPolicy = 0;
    memoryPolicy |= MemoryPolicy.NO_CACHE.index;
    assertThat(MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)).isFalse();
  }

  @Test public void readFromMemoryCache() {
    int memoryPolicy = 0;
    memoryPolicy |= MemoryPolicy.NO_STORE.index;
    assertThat(MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)).isTrue();
  }

  @Test public void dontWriteToMemoryCache() {
    int memoryPolicy = 0;
    memoryPolicy |= MemoryPolicy.NO_STORE.index;
    assertThat(MemoryPolicy.shouldWriteToMemoryCache(memoryPolicy)).isFalse();
  }

  @Test public void writeToMemoryCache() {
    int memoryPolicy = 0;
    memoryPolicy |= MemoryPolicy.NO_CACHE.index;
    assertThat(MemoryPolicy.shouldWriteToMemoryCache(memoryPolicy)).isTrue();
  }
}
