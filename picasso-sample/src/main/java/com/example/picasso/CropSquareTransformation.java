package com.example.picasso;

import android.graphics.Bitmap;
import com.squareup.picasso.Transformation;

/** Custom transformation class that crops an image to make it square. */
final class CropSquareTransformation implements Transformation {
  @Override public Bitmap transform(Bitmap source) {
    int size = Math.min(source.getWidth(), source.getHeight());

    int x = (source.getWidth() - size) / 2;
    int y = (source.getHeight() - size) / 2;

    Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
    if (squaredBitmap != source) {
      source.recycle();
    }
    return squaredBitmap;
  }

  @Override public String key() {
    return "square()";
  }
}
