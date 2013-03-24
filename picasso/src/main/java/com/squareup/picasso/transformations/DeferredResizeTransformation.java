package com.squareup.picasso.transformations;

import android.graphics.Bitmap;
import android.widget.ImageView;
import com.squareup.picasso.Transformation;
import java.lang.ref.WeakReference;

public class DeferredResizeTransformation implements Transformation {
  private final WeakReference<ImageView> target;

  public DeferredResizeTransformation(ImageView target) {
    this.target = new WeakReference<ImageView>(target);
  }

  @Override public Bitmap transform(Bitmap source) {
    ImageView target = this.target.get();
    if (target == null) return source;

    int width = target.getMeasuredWidth();
    int height = target.getMeasuredHeight();
    if (width == 0 || height == 0) {
      return source;
    }

    return ResizeTransformation.resize(source, width, height);
  }

  @Override public String toString() {
    return "deferredResize()";
  }
}
