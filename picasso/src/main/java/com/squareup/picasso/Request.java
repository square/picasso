package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.Future;

import static com.squareup.picasso.Utils.createKey;

class Request implements Runnable {
  static final int DEFAULT_RETRY_COUNT = 2;

  enum LoadedFrom {
    MEMORY(Color.GREEN),
    DISK(Color.YELLOW),
    NETWORK(Color.RED);

    final int debugColor;

    private LoadedFrom(int debugColor) {
      this.debugColor = debugColor;
    }
  }

  final Picasso picasso;
  final Uri uri;
  final int resourceId;
  final WeakReference<ImageView> target;
  final PicassoBitmapOptions options;
  final List<Transformation> transformations;
  final boolean skipCache;
  final boolean noFade;
  final int errorResId;
  final Drawable errorDrawable;
  final String key;

  Future<?> future;
  Bitmap result;
  LoadedFrom loadedFrom;
  int retryCount;
  boolean retryCancelled;

  Request(Picasso picasso, Uri uri, int resourceId, ImageView imageView,
      PicassoBitmapOptions options, List<Transformation> transformations, boolean skipCache,
      boolean noFade, int errorResId, Drawable errorDrawable) {
    this.picasso = picasso;
    this.uri = uri;
    this.resourceId = resourceId;
    this.target = new WeakReference<ImageView>(imageView);
    this.options = options;
    this.transformations = transformations;
    this.skipCache = skipCache;
    this.noFade = noFade;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.retryCount = DEFAULT_RETRY_COUNT;
    this.key = createKey(this);
  }

  Object getTarget() {
    return target.get();
  }

  void complete() {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", this));
    }

    ImageView target = this.target.get();
    if (target != null) {
      Context context = picasso.context;
      boolean debugging = picasso.debugging;
      PicassoDrawable.setBitmap(target, context, result, loadedFrom, noFade, debugging);
    }
  }

  void error() {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    if (errorResId != 0) {
      target.setImageResource(errorResId);
    } else if (errorDrawable != null) {
      target.setImageDrawable(errorDrawable);
    }
  }

  @Override public void run() {
    try {
      // Change the thread name to contain the target URL for debugging purposes.
      Thread.currentThread().setName(Utils.THREAD_PREFIX + getName());

      picasso.run(this);
    } catch (final Throwable e) {
      // If an unexpected exception happens, we should crash the app instead of letting the
      // executor swallow it.
      picasso.handler.post(new Runnable() {
        @Override public void run() {
          throw new RuntimeException("An unexpected exception occurred", e);
        }
      });
    } finally {
      Thread.currentThread().setName(Utils.THREAD_IDLE_NAME);
    }
  }

  private String getName() {
    Uri uri = this.uri;
    return uri != null ? uri.getPath() : Integer.toString(resourceId);
  }

  @Override public String toString() {
    return "Request["
        + "hashCode="
        + hashCode()
        + ", picasso="
        + picasso
        + ", uri="
        + uri
        + ", resourceId="
        + resourceId
        + ", target="
        + target
        + ", options="
        + options
        + ", transformations="
        + transformationKeys()
        + ", future="
        + future
        + ", result="
        + result
        + ", retryCount="
        + retryCount
        + ", loadedFrom="
        + loadedFrom
        + ']';
  }

  String transformationKeys() {
    if (transformations == null) {
      return "[]";
    }

    StringBuilder sb = new StringBuilder(transformations.size() * 16);

    sb.append('[');
    boolean first = true;
    for (Transformation transformation : transformations) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(transformation.key());
    }
    sb.append(']');

    return sb.toString();
  }
}
