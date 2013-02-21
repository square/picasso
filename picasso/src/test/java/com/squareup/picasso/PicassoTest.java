package com.squareup.picasso;

import android.app.Activity;
import java.util.concurrent.ExecutorService;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Mockito.mock;

@RunWith(PicassoTestRunner.class)
public class PicassoTest {
  private Loader loader = mock(Loader.class);
  private ExecutorService executor = mock(ExecutorService.class);
  private Cache cache = mock(Cache.class);

  @Ignore // Robolectric doesn't like HttpResponseCache for some reason...
  @Test public void singleIsLazilyInitialized() {
    assertThat(Picasso.singleton).isNull();
    Picasso.with(new Activity());
    assertThat(Picasso.singleton).isNotNull();
    Picasso.singleton = null;
  }

  @Test public void builderInvalidLoader() {
    try {
      new Picasso.Builder().loader(null);
      fail("Null Loader should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder().loader(loader).loader(loader);
      fail("Setting Loader twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidExecutor() {
    try {
      new Picasso.Builder().executor(null);
      fail("Null Executor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder().executor(executor).executor(executor);
      fail("Setting Executor twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderInvalidCache() {
    try {
      new Picasso.Builder().memoryCache(null);
      fail("Null Cache should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Picasso.Builder().memoryCache(cache).memoryCache(cache);
      fail("Setting Cache twice should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void builderMissingRequired() {
    try {
      new Picasso.Builder().build();
      fail("Loader and executor are required.");
    } catch (IllegalStateException expected) {
    }
    try {
      new Picasso.Builder().loader(loader).build();
      fail("Loader and executor are required.");
    } catch (IllegalStateException expected) {
    }
    try {
      new Picasso.Builder().executor(executor).build();
      fail("Loader and executor are required.");
    } catch (IllegalStateException expected) {
    }
  }
}
