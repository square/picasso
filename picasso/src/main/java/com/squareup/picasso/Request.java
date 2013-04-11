package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import com.squareup.picasso.transformations.DeferredResizeTransformation;
import com.squareup.picasso.transformations.ResizeTransformation;
import com.squareup.picasso.transformations.RotationTransformation;
import com.squareup.picasso.transformations.ScaleTransformation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static com.squareup.picasso.Utils.checkNotMain;
import static com.squareup.picasso.Utils.createKey;

public class Request implements Runnable {
  private static final int DEFAULT_RETRY_COUNT = 2;

  enum Type {
    CONTENT,
    FILE,
    STREAM,
    RESOURCE
  }

  final Picasso picasso;
  final String path;
  final int resourceId;
  final String key;
  final Type type;
  final int errorResId;
  final WeakReference<ImageView> target;
  final BitmapFactory.Options bitmapOptions;
  final List<Transformation> transformations;
  final RequestMetrics metrics;
  final Drawable errorDrawable;

  Future<?> future;
  Bitmap result;
  int retryCount;
  boolean retryCancelled;

  Request(Picasso picasso, String path, int resourceId, ImageView imageView,
      BitmapFactory.Options bitmapOptions, List<Transformation> transformations,
      RequestMetrics metrics, Type type, int errorResId, Drawable errorDrawable) {
    this.picasso = picasso;
    this.path = path;
    this.resourceId = resourceId;
    this.type = type;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.target = new WeakReference<ImageView>(imageView);
    this.bitmapOptions = bitmapOptions;
    this.transformations = transformations;
    this.metrics = metrics;
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

    ImageView imageView = target.get();
    if (imageView != null) {
      imageView.setImageBitmap(result);
    }
  }

  void error() {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }

    if (errorResId != 0) {
      target.setImageResource(errorResId);
      return;
    }

    if (errorDrawable != null) {
      target.setImageDrawable(errorDrawable);
    }
  }

  @Override public void run() {
    try {
      picasso.run(this);
    } catch (final Throwable e) {
      // If an unexpected exception happens, we should crash the app instead of letting the
      // executor swallow it.
      new Handler(Looper.getMainLooper()).post(new Runnable() {
        @Override public void run() {
          throw new RuntimeException("An unexpected exception occurred", e);
        }
      });
    }
  }

  @Override public String toString() {
    return "Request["
        + "hashCode="
        + hashCode()
        + ", picasso="
        + picasso
        + ", path="
        + path
        + ", resourceId="
        + resourceId
        + ", target="
        + target
        + ", bitmapOptions="
        + bitmapOptions
        + ", transformations="
        + transformationKeys()
        + ", metrics="
        + metrics
        + ", future="
        + future
        + ", result="
        + result
        + ", retryCount="
        + retryCount
        + ']';
  }

  String transformationKeys() {
    if (transformations.isEmpty()) {
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

  @SuppressWarnings("UnusedDeclaration") // Public API.
  public static class Builder {
    private final Picasso picasso;
    private final String path;
    private final int resourceId;
    private final Type type;
    private final List<Transformation> transformations;

    private boolean deferredResize;
    private int placeholderResId;
    private int errorResId;
    private BitmapFactory.Options bitmapOptions;
    private Drawable placeholderDrawable;
    private Drawable errorDrawable;

    Builder(Picasso picasso, String path, int resourceId, Type type) {
      if (picasso == null) {
        throw new AssertionError();
      }
      boolean hasPath = path != null && path.trim().length() != 0;
      boolean hasResource = resourceId != 0;
      if (!(hasPath ^ hasResource)) {
          throw new IllegalArgumentException("A valid path or valid resource must be provided.");
      }
      this.picasso = picasso;
      this.path = path;
      this.resourceId = resourceId;
      this.type = type;
      this.transformations = new ArrayList<Transformation>(4);
    }

    public Builder placeholder(int placeholderResId) {
      if (placeholderResId == 0) {
        throw new IllegalArgumentException("Placeholder image resource invalid.");
      }
      if (placeholderDrawable != null) {
        throw new IllegalStateException("Placeholder image already set.");
      }
      this.placeholderResId = placeholderResId;
      return this;
    }

    public Builder placeholder(Drawable placeholderDrawable) {
      if (placeholderDrawable == null) {
        throw new IllegalArgumentException("Placeholder image may not be null.");
      }
      if (placeholderResId != 0) {
        throw new IllegalStateException("Placeholder image already set.");
      }
      this.placeholderDrawable = placeholderDrawable;
      return this;
    }

    public Builder error(int errorResId) {
      if (errorResId == 0) {
        throw new IllegalArgumentException("Error image resource invalid.");
      }
      if (errorDrawable != null) {
        throw new IllegalStateException("Error image already set.");
      }
      this.errorResId = errorResId;
      return this;
    }

    public Builder error(Drawable errorDrawable) {
      if (errorDrawable == null) {
        throw new IllegalArgumentException("Error image may not be null.");
      }
      if (this.errorResId != 0) {
        throw new IllegalStateException("Error image already set.");
      }
      this.errorDrawable = errorDrawable;
      return this;
    }

    public Builder bitmapOptions(BitmapFactory.Options bitmapOptions) {
      if (bitmapOptions == null) {
        throw new IllegalArgumentException("Bitmap options may not be null.");
      }
      this.bitmapOptions = bitmapOptions;
      return this;
    }

    public Builder fit() {
      deferredResize = true;
      return this;
    }

    public Builder resize(int targetWidth, int targetHeight) {
      if (targetWidth <= 0) {
        throw new IllegalArgumentException("Width must be positive number.");
      }
      if (targetHeight <= 0) {
        throw new IllegalArgumentException("Height must be positive number.");
      }
      return transform(new ResizeTransformation(targetWidth, targetHeight));
    }

    public Builder scale(float factor) {
      if (factor <= 0) {
        throw new IllegalArgumentException("Scale factor must be positive number.");
      }
      return transform(new ScaleTransformation(factor));
    }

    public Builder rotate(float degrees) {
      return transform(new RotationTransformation(degrees));
    }

    public Builder rotate(float degrees, float pivotX, float pivotY) {
      return transform(new RotationTransformation(degrees, pivotX, pivotY));
    }

    public Builder transform(Transformation transformation) {
      if (transformation == null) {
        throw new IllegalArgumentException("Transformation may not be null.");
      }
      this.transformations.add(transformation);
      return this;
    }

    public Bitmap get() {
      checkNotMain();
      Request request =
          new Request(picasso, path, resourceId, null, bitmapOptions, transformations, null, type,
              errorResId, errorDrawable);
      return picasso.run(request);
    }

    public void into(Target target) {
      if (target == null) {
        throw new IllegalArgumentException("Target cannot be null.");
      }

      Bitmap bitmap = picasso.quickMemoryCacheCheck(target, createKey(path, transformations, null));
      if (bitmap != null) {
        target.onSuccess(bitmap);
        return;
      }

      RequestMetrics metrics = createRequestMetrics();
      Request request =
          new TargetRequest(picasso, path, resourceId, target, bitmapOptions, transformations,
              metrics, type, errorResId, errorDrawable);
      picasso.submit(request);
    }

    public void into(ImageView target) {
      if (target == null) {
        throw new IllegalArgumentException("Target cannot be null.");
      }

      if (deferredResize) {
        transformations.add(new DeferredResizeTransformation(target));
      }

      Bitmap bitmap = picasso.quickMemoryCacheCheck(target, createKey(path, transformations, null));
      if (bitmap != null) {
        target.setImageBitmap(bitmap);
        return;
      }

      if (placeholderDrawable != null) {
        target.setImageDrawable(placeholderDrawable);
      }

      if (placeholderResId != 0) {
        target.setImageResource(placeholderResId);
      }

      RequestMetrics metrics = new RequestMetrics();
      Request request =
          new Request(picasso, path, resourceId, target, bitmapOptions, transformations, metrics,
              type, errorResId, errorDrawable);
      picasso.submit(request);
    }

    private RequestMetrics createRequestMetrics() {
      RequestMetrics metrics = null;
      if (picasso.debugging) {
        metrics = new RequestMetrics();
        metrics.createdTime = System.nanoTime();
      }
      return metrics;
    }
  }
}
