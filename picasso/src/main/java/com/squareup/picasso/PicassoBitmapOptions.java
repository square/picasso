package com.squareup.picasso;

import android.graphics.BitmapFactory;

class PicassoBitmapOptions extends BitmapFactory.Options {
  final int targetWidth;
  final int targetHeight;
  final int targetScale;

  PicassoBitmapOptions(int targetWidth, int targetHeight, int targetScale) {
    this.targetWidth = targetWidth;
    this.targetHeight = targetHeight;
    this.targetScale = targetScale;
  }
}
