package com.squareup.picasso.transformations;

import android.graphics.Bitmap;
import com.squareup.picasso.Transformation;

public class ResizeTransformation implements Transformation {

  private final int targetWidth;
  private final int targetHeight;

  public ResizeTransformation(int targetWidth, int targetHeight) {
    this.targetWidth = targetWidth;
    this.targetHeight = targetHeight;
  }

  @Override public Bitmap transform(Bitmap source) {
    return resize(source, targetWidth, targetHeight);
  }

  @Override public String toString() {
    return "resize(" + targetWidth + ',' + targetHeight + ')';
  }

  static Bitmap resize(Bitmap source, int targetWidth, int targetHeight) {
    if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) return source;

    Bitmap transformed = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false);
    source.recycle();
    return transformed;
  }
}
