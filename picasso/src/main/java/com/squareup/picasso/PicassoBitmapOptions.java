package com.squareup.picasso;

import android.graphics.BitmapFactory;

class PicassoBitmapOptions extends BitmapFactory.Options {
  int targetWidth;
  int targetHeight;
  boolean deferredResize;
  boolean centerCrop;
  boolean centerInside;

  float targetScaleX;
  float targetScaleY;

  float targetRotation;
  float targetPivotX;
  float targetPivotY;
  boolean hasRotationPivot;
}
