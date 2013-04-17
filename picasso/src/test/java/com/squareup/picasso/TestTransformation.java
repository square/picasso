package com.squareup.picasso;

import android.graphics.Bitmap;

class TestTransformation implements Transformation {
  private final String key;
  private final Bitmap result;

  TestTransformation(String key) {
    this(key, Bitmap.createBitmap(10, 10, null));
  }

  TestTransformation(String key, Bitmap result) {
    this.key = key;
    this.result = result;
  }

  @Override public Bitmap transform(Bitmap source) {
    source.recycle();
    return result;
  }

  @Override public String key() {
    return key;
  }
}
