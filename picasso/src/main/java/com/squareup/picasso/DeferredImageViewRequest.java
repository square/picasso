package com.squareup.picasso;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.util.List;

@SuppressWarnings("deprecation")
class DeferredImageViewRequest extends ImageViewRequest
    implements ViewTreeObserver.OnGlobalLayoutListener {

  DeferredImageViewRequest(Picasso picasso, Uri uri, int resourceId, ImageView imageView,
      PicassoBitmapOptions options, List<Transformation> transformations, boolean skipCache,
      boolean noFade, int errorResId, Drawable errorDrawable, String key, Callback callback) {
    super(picasso, uri, resourceId, imageView, options, transformations, skipCache, noFade,
        errorResId, errorDrawable, key, callback);
    ViewTreeObserver observer = imageView.getViewTreeObserver();
    observer.addOnGlobalLayoutListener(this);
  }

  @Override void cancel() {
    super.cancel();
    ImageView target = getTarget();
    if (target != null) {
      ViewTreeObserver observer = target.getViewTreeObserver();
      if (observer.isAlive()) {
        observer.removeGlobalOnLayoutListener(this);
      }
    }
  }

  @Override public void onGlobalLayout() {
    ImageView target = getTarget();
    if (target != null) {
      ViewTreeObserver observer = target.getViewTreeObserver();
      if (observer.isAlive()) {
        observer.removeGlobalOnLayoutListener(this);
      }

      options.targetWidth = target.getMeasuredWidth();
      options.targetHeight = target.getMeasuredHeight();
      options.inJustDecodeBounds = true;

      picasso.submit(this);
    }
  }
}
