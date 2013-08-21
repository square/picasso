package com.squareup.picasso;

import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

class DeferredRequestCreator implements ViewTreeObserver.OnGlobalLayoutListener {

  final RequestCreator creator;
  final WeakReference<ImageView> target;

  DeferredRequestCreator(RequestCreator creator, ImageView target) {
    this.creator = creator;
    this.target = new WeakReference<ImageView>(target);
    target.getViewTreeObserver().addOnGlobalLayoutListener(this);
  }

  @Override public void onGlobalLayout() {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    ViewTreeObserver vto = target.getViewTreeObserver();
    if (!vto.isAlive()) {
      return;
    }

    int width = target.getMeasuredWidth();
    int height = target.getMeasuredHeight();

    if (width <= 0 || height <= 0) {
      return;
    }

    //noinspection deprecation
    vto.removeGlobalOnLayoutListener(this);

    this.creator.unfit().resize(width, height).into(target);
  }

  void cancel() {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    ViewTreeObserver vto = target.getViewTreeObserver();
    if (!vto.isAlive()) {
      return;
    }
    //noinspection deprecation
    vto.removeGlobalOnLayoutListener(this);
  }
}
