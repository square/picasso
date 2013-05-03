package com.squareup.picasso;

import java.lang.ref.WeakReference;
import java.util.List;

final class TargetRequest extends Request {

  private final WeakReference<Target> weakTarget;
  private final Target strongTarget;

  TargetRequest(Picasso picasso, String path, int resourceId, Target target, boolean strong,
      PicassoBitmapOptions bitmapOptions, List<Transformation> transformations, Type type,
      boolean skipCache) {
    super(picasso, path, resourceId, null, bitmapOptions, transformations, type, skipCache, false,
        0, null);
    this.weakTarget = strong ? null : new WeakReference<Target>(target);
    this.strongTarget = strong ? target : null;
  }

  @Override Target getTarget() {
    return strongTarget != null ? strongTarget : weakTarget.get();
  }

  @Override void complete() {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", this));
    }
    Target target = getTarget();
    if (target != null) {
      target.onSuccess(result);
      if (result.isRecycled()) {
        throw new IllegalStateException("Target callback must not recycle bitmap!");
      }
    }
  }

  @Override void error() {
    Target target = getTarget();
    if (target == null) {
      return;
    }
    target.onError();
  }
}
