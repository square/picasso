package com.squareup.picasso;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.TestOnly;

import static com.squareup.picasso.Utils.checkNotMain;
import static com.squareup.picasso.Utils.createKey;

public class Request implements Runnable {
  static final int DEFAULT_RETRY_COUNT = 2;

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
  final PicassoBitmapOptions options;
  final List<Transformation> transformations;
  final RequestMetrics metrics;
  final Drawable errorDrawable;

  Future<?> future;
  Bitmap result;
  int retryCount;
  boolean retryCancelled;

  Request(Picasso picasso, String path, int resourceId, ImageView imageView,
      PicassoBitmapOptions options, List<Transformation> transformations, RequestMetrics metrics,
      Type type, int errorResId, Drawable errorDrawable) {
    this.picasso = picasso;
    this.path = path;
    this.resourceId = resourceId;
    this.type = type;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.target = new WeakReference<ImageView>(imageView);
    this.options = options;
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
    } else if (errorDrawable != null) {
      target.setImageDrawable(errorDrawable);
    }
  }

  @Override public void run() {
    try {
      picasso.resolveRequest(this);
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
        + ", options="
        + options
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

  @SuppressWarnings("UnusedDeclaration") // Public API.
  public static class Builder {
    private final Picasso picasso;
    private final String path;
    private final int resourceId;
    private final Type type;

    PicassoBitmapOptions options;
    private List<Transformation> transformations;
    private int placeholderResId;
    private Drawable placeholderDrawable;
    private int errorResId;
    private Drawable errorDrawable;

    Builder(Picasso picasso, int resourceId) {
      this.picasso = picasso;
      this.path = null;
      this.resourceId = resourceId;
      this.type = Type.RESOURCE;
      this.transformations = new ArrayList<Transformation>(4);
    }

    Builder(Picasso picasso, String path, Type type) {
      this.picasso = picasso;
      this.path = path;
      this.resourceId = 0;
      this.type = type;
    }

    @TestOnly Builder() {
      this.picasso = null;
      this.path = null;
      this.resourceId = 0;
      this.type = null;
    }

    private PicassoBitmapOptions getOptions() {
      if (options == null) {
        options = new PicassoBitmapOptions();
      }
      return options;
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
      if (errorResId != 0) {
        throw new IllegalStateException("Error image already set.");
      }
      this.errorDrawable = errorDrawable;
      return this;
    }

    public Builder fit() {
      PicassoBitmapOptions options = getOptions();

      if (options.targetWidth != 0 || options.targetHeight != 0) {
        throw new IllegalStateException("Fit cannot be used with resize.");
      }

      options.deferredResize = true;
      return this;
    }

    public Builder resizeDimen(int targetWidthResId, int targetHeightResId) {
      Resources resources = picasso.context.getResources();
      int targetWidth = resources.getDimensionPixelSize(targetWidthResId);
      int targetHeight = resources.getDimensionPixelSize(targetHeightResId);
      return resize(targetWidth, targetHeight);
    }

    public Builder resize(int targetWidth, int targetHeight) {
      if (targetWidth <= 0) {
        throw new IllegalArgumentException("Width must be positive number.");
      }
      if (targetHeight <= 0) {
        throw new IllegalArgumentException("Height must be positive number.");
      }

      PicassoBitmapOptions options = getOptions();

      if (options.targetWidth != 0 || options.targetHeight != 0) {
        throw new IllegalStateException("Resize may only be called once.");
      }
      if (options.deferredResize) {
        throw new IllegalStateException("Resize cannot be used with fit.");
      }

      options.targetWidth = targetWidth;
      options.targetHeight = targetHeight;

      // Use bounds decoding optimization when reading local resources.
      if (type != Type.STREAM) {
        options.inJustDecodeBounds = true;
      }

      return this;
    }

    public Builder scale(float factor) {
      if (factor != 1) {
        scale(factor, factor);
      }
      return this;
    }

    public Builder scale(float factorX, float factorY) {
      if (factorX == 0 || factorY == 0) {
        throw new IllegalArgumentException("Scale factor must be positive number.");
      }
      if (factorX != 1 && factorY != 1) {
        PicassoBitmapOptions options = getOptions();

        if (options.targetScaleX != 0 || options.targetScaleY != 0) {
          throw new IllegalStateException("Scale may only be called once.");
        }

        options.targetScaleX = factorX;
        options.targetScaleY = factorY;
      }
      return this;
    }

    public Builder rotate(float degrees) {
      if (degrees != 0) {
        PicassoBitmapOptions options = getOptions();
        options.targetRotation = degrees;
      }
      return this;
    }

    public Builder rotate(float degrees, float pivotX, float pivotY) {
      if (degrees != 0) {
        PicassoBitmapOptions pbo = getOptions();
        pbo.targetRotation = degrees;
        pbo.targetPivotX = pivotX;
        pbo.targetPivotY = pivotY;
        pbo.hasRotationPivot = true;
      }
      return this;
    }

    public Builder transform(Transformation transformation) {
      if (transformation == null) {
        throw new IllegalArgumentException("Transformation may not be null.");
      }
      if (transformations == null) {
        transformations = new ArrayList<Transformation>(2);
      }
      transformations.add(transformation);
      return this;
    }

    public Bitmap get() {
      checkNotMain();
      Request request =
          new Request(picasso, path, resourceId, null, options, transformations, null, type,
              errorResId, errorDrawable);
      return picasso.resolveRequest(request);
    }

    public void into(Target target) {
      if (target == null) {
        throw new IllegalArgumentException("Target cannot be null.");
      }

      Bitmap bitmap = picasso.quickMemoryCacheCheck(target,
          createKey(path, resourceId, options, transformations, null));
      if (bitmap != null) {
        target.onSuccess(bitmap);
        return;
      }

      RequestMetrics metrics = createRequestMetrics();
      Request request =
          new TargetRequest(picasso, path, resourceId, target, options, transformations, metrics,
              type, errorResId, errorDrawable);
      picasso.submit(request);
    }

    public void into(ImageView target) {
      if (target == null) {
        throw new IllegalArgumentException("Target cannot be null.");
      }

      Bitmap bitmap = picasso.quickMemoryCacheCheck(target,
          createKey(path, resourceId, options, transformations, null));
      if (bitmap != null) {
        target.setImageBitmap(bitmap);
        return;
      }

      if (placeholderDrawable != null) {
        target.setImageDrawable(placeholderDrawable);
      } else if (placeholderResId != 0) {
        target.setImageResource(placeholderResId);
      }

      RequestMetrics metrics = new RequestMetrics();
      Request request =
          new Request(picasso, path, resourceId, target, options, transformations, metrics, type,
              errorResId, errorDrawable);
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
