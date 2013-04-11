package com.squareup.picasso;

import android.graphics.drawable.Drawable;
import java.lang.ref.WeakReference;
import java.util.List;

public class TargetRequest extends Request {

  private WeakReference<Target> target;

  TargetRequest(Picasso picasso, String path, int resourceId, Target target,
      PicassoBitmapOptions bitmapOptions, List<Transformation> transformations,
      RequestMetrics metrics, Type type, int errorResId, Drawable errorDrawable) {
    super(picasso, path, resourceId, null, bitmapOptions, transformations, metrics, type,
        errorResId, errorDrawable);
    this.target = new WeakReference<Target>(target);
  }

  @Override Target getTarget() {
    return this.target.get();
  }

  @Override void complete() {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", this));
    }
    Target target = this.target.get();
    if (target != null) {
      target.onSuccess(result);
    }
  }

  @Override void error() {
    Target target = this.target.get();
    if (target == null) {
      return;
    }
    target.onError();
  }
}
