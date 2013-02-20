package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import com.squareup.picasso.transformations.DeferredResizeTransformation;
import com.squareup.picasso.transformations.ResizeTransformation;
import com.squareup.picasso.transformations.RotationTransformation;
import com.squareup.picasso.transformations.ScaleTransformation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static com.squareup.picasso.Picasso.checkNotMain;

public class Request implements Runnable {

  final Picasso picasso;
  final String path;
  final WeakReference<ImageView> target;
  final BitmapFactory.Options bitmapOptions;
  final List<Transformation> transformations;
  final RequestMetrics metrics;

  private Future<?> future;
  private Bitmap result;
  int retryCount;

  Request(Picasso picasso, String path, ImageView imageView, BitmapFactory.Options bitmapOptions,
      List<Transformation> transformations, RequestMetrics metrics) {
    this.picasso = picasso;
    this.path = path;
    this.target = new WeakReference<ImageView>(imageView);
    this.bitmapOptions = bitmapOptions;
    this.transformations = transformations;
    this.metrics = metrics;
    this.retryCount = 2;
  }

  @Override public void run() {
    picasso.run(this);
  }

  @Override public String toString() {
    return "Request{" +
        "picasso=" + picasso +
        ", path=" + path +
        ", target=" + target +
        ", bitmapOptions=" + bitmapOptions +
        ", transformations=" + transformations +
        ", metrics=" + metrics +
        ", future=" + future +
        ", result=" + result +
        ", retryCount=" + retryCount +
        '}';
  }

  Future<?> getFuture() {
    return future;
  }

  RequestMetrics getMetrics() {
    return metrics;
  }

  public String getPath() {
    return path;
  }

  public ImageView getTarget() {
    return target.get();
  }

  public Bitmap getResult() {
    return result;
  }

  void setResult(Bitmap result) {
    this.result = result;
  }

  void setFuture(Future<?> future) {
    this.future = future;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Builder {
    private final Picasso picasso;
    private final String path;
    private final List<Transformation> transformations;

    private int placeholderResId;
    private boolean deferredResize;
    private BitmapFactory.Options bitmapOptions;
    private Drawable placeholderDrawable;

    public Builder(Picasso picasso, String path) {
      this.picasso = picasso;
      this.path = path;
      this.transformations = new ArrayList<Transformation>(4);
    }

    public Builder placeholder(int placeholderResId) {
      if (placeholderDrawable != null) {
        throw new IllegalStateException("Placeholder already set!");
      }
      this.placeholderResId = placeholderResId;
      return this;
    }

    public Builder placeholder(Drawable placeholderDrawable) {
      if (placeholderResId != 0) {
        throw new IllegalStateException("Placeholder already set!");
      }
      this.placeholderDrawable = placeholderDrawable;
      return this;
    }

    public Builder bitmapOptions(BitmapFactory.Options bitmapOptions) {
      this.bitmapOptions = bitmapOptions;
      return this;
    }

    public Builder resize() {
      deferredResize = true;
      return this;
    }

    public Builder resize(int targetWidth, int targetHeight) {
      return transform(new ResizeTransformation(targetWidth, targetHeight));
    }

    public Builder scale(float factor) {
      return transform(new ScaleTransformation(factor));
    }

    public Builder rotate(float degrees) {
      return transform(new RotationTransformation(degrees));
    }

    public Builder rotate(float degrees, float pivotX, float pivotY) {
      return transform(new RotationTransformation(degrees, pivotX, pivotY));
    }

    public Builder transform(Transformation transformation) {
      this.transformations.add(transformation);
      return this;
    }

    public Bitmap get() {
      checkNotMain();
      Request request = new Request(picasso, path, null, bitmapOptions, transformations, null);
      return picasso.run(request);
    }

    public void into(ImageView target) {
      if (target == null) {
        throw new IllegalStateException("Target cannot be null.");
      }

      RequestMetrics metrics = null;
      if (picasso.debugging) {
        metrics = new RequestMetrics();
        metrics.createdTime = System.nanoTime();
      }

      // Avoids Request object allocation.
      if (picasso.quickCacheCheck(target, path, metrics)) return;

      if (placeholderDrawable != null) {
        target.setImageDrawable(placeholderDrawable);
      }

      if (placeholderResId != 0) {
        target.setImageResource(placeholderResId);
      }

      if (deferredResize) {
        transformations.add(new DeferredResizeTransformation(target));
      }

      Request request = new Request(picasso, path, target, bitmapOptions, transformations, metrics);
      picasso.submit(request);
    }
  }
}
