package com.squareup.picasso;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(PicassoTestRunner.class)
public class PicassoDrawableTest {
  @Test public void createWithBitmapCacheHit() {
    // TODO create with a bitmap
  }

  @Test public void withPlaceholderToBitmap() {
    // TODO given with a placeholder, call setBitmap from disk and ensure there's a cross fade
  }

  @Test public void withBitmapRecycleToPlaceholder() {
    // TODO given with a bitmap, call setPlaceholder and verify behavior
  }

  @Test public void withBitmapRecycleToBitmapCacheHit() {
    // TODO given with a bitmap, call setBitmap from mem and ensure there's no cross fade
  }

  @Test public void withBitmapRecycleToBitmap() {
    // TODO given with a bitmap, call setBitmap from disk and ensure there's a cross fade
  }
}
