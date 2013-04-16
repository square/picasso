package com.squareup.picasso.transformations;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import com.squareup.picasso.Transformation;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

public class ScaleTransformation implements Transformation {

  private static final Paint BITMAP_PAINT = new Paint(FILTER_BITMAP_FLAG);
  private static final ThreadLocal<Canvas> CANVAS_LOCAL = new ThreadLocal<Canvas>() {
    @Override protected Canvas initialValue() {
      return new Canvas();
    }
  };

  private final float factor;

  public ScaleTransformation(float factor) {
    this.factor = factor;
  }

  @Override public Bitmap transform(Bitmap source) {
    if (factor == 1.0f) return source;

    int width = (int) (source.getWidth() * factor);
    int height = (int) (source.getHeight() * factor);

    final Bitmap transformed = Bitmap.createBitmap(width, height, ARGB_8888);

    Canvas canvas = CANVAS_LOCAL.get();
    canvas.setBitmap(transformed);
    canvas.save();
    canvas.scale(factor, factor);
    canvas.drawBitmap(source, 0, 0, BITMAP_PAINT);
    canvas.restore();

    source.recycle();
    return transformed;
  }

  @Override public String key() {
    return "scale(" + factor + ')';
  }
}
