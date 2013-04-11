package com.squareup.picasso;

import android.graphics.Bitmap;

public interface Transformation {

  /**
   * You may return the source bitmap or a new bitmap. If you return a new bitmap, you should call
   * source.recycle()
   */
  Bitmap transform(Bitmap source);

  /**
   * Returns a unique key for the transformation, used for caching purposes. If the transformation
   * has parameters (e.g. size, scale factor, etc) then these should be part of the key.
   */
  String key();
}
