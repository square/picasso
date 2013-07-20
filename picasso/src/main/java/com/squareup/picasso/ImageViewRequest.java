package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;
import java.util.List;

class ImageViewRequest extends Request<ImageView> {

  private Callback callback;

  ImageViewRequest(Picasso picasso, Uri uri, int resourceId, ImageView imageView,
      PicassoBitmapOptions options, List<Transformation> transformations, boolean skipCache,
      boolean noFade, int errorResId, Drawable errorDrawable, String key, Callback callback) {
    super(picasso, uri, resourceId, imageView, options, transformations, skipCache, noFade,
        errorResId, errorDrawable, key);
    this.callback = callback;
  }

  @Override public void complete(Bitmap result, LoadedFrom from) {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", this));
    }

    ImageView target = this.target.get();
    if (target == null) {
      return;
    }

    Context context = picasso.context;
    boolean debugging = picasso.debugging;
    PicassoDrawable.setBitmap(target, context, result, from, noFade, debugging);

    if (callback != null) {
      callback.onImageLoaded();
    }
  }

  @Override public void error() {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    if (errorResId != 0) {
      target.setImageResource(errorResId);
    } else if (errorDrawable != null) {
      target.setImageDrawable(errorDrawable);
    }

    if (callback != null) {
      callback.onImageFailed();
    }
  }

  @Override void cancel() {
    super.cancel();
    if (callback != null) {
      callback = null;
    }
  }
}
