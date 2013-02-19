package com.squareup.picasso.transformations;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import com.squareup.picasso.Transformation;

public class RotationTransformation implements Transformation {

  private static final int DEFAULT_PIVOT = -1;
  private static final Paint BITMAP_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
  private static final ThreadLocal<Canvas> CANVAS_LOCAL = new ThreadLocal<Canvas>() {
    @Override protected Canvas initialValue() {
      return new Canvas();
    }
  };

  private final float degrees;
  private final float pivotX;
  private final float pivotY;

  public RotationTransformation(float degrees) {
    this(degrees, DEFAULT_PIVOT, DEFAULT_PIVOT);
  }

  public RotationTransformation(float degrees, float pivotX, float pivotY) {
    this.degrees = degrees;
    this.pivotX = pivotX;
    this.pivotY = pivotY;
  }

  @Override public Bitmap transform(Bitmap source) {
    try {
      float pivotX = this.pivotX;
      float pivotY = this.pivotY;

      boolean defaultPivot = pivotX == DEFAULT_PIVOT && pivotY == DEFAULT_PIVOT;
      if (degrees == 0 && defaultPivot) return source;

      Bitmap transformed =
          Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);

      Canvas canvas = CANVAS_LOCAL.get();
      canvas.setBitmap(transformed);
      canvas.save();

      if (defaultPivot) {
        pivotX = source.getWidth() / 2f;
        pivotY = source.getHeight() / 2f;
      }

      canvas.rotate(degrees, pivotX, pivotY);
      canvas.drawBitmap(source, 0, 0, BITMAP_PAINT);
      canvas.restore();

      source.recycle();
      return transformed;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override public String toString() {
    return "rotate(" + degrees + ',' + pivotX + ',' + pivotY + ')';
  }
}
