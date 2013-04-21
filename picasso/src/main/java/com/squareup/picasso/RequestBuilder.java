package com.squareup.picasso;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.TestOnly;

import static com.squareup.picasso.Utils.checkNotMain;
import static com.squareup.picasso.Utils.createKey;

@SuppressWarnings("UnusedDeclaration") // Public API.
public class RequestBuilder {
  private final Picasso picasso;
  private final String path;
  private final int resourceId;
  private final Request.Type type;

  PicassoBitmapOptions options;
  private List<Transformation> transformations;
  private int placeholderResId;
  private Drawable placeholderDrawable;
  private int errorResId;
  private Drawable errorDrawable;

  RequestBuilder(Picasso picasso, int resourceId) {
    this.picasso = picasso;
    this.path = null;
    this.resourceId = resourceId;
    this.type = Request.Type.RESOURCE;
    this.transformations = new ArrayList<Transformation>(4);
  }

  RequestBuilder(Picasso picasso, String path, Request.Type type) {
    this.picasso = picasso;
    this.path = path;
    this.resourceId = 0;
    this.type = type;
  }

  @TestOnly RequestBuilder() {
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

  public RequestBuilder placeholder(int placeholderResId) {
    if (placeholderResId == 0) {
      throw new IllegalArgumentException("Placeholder image resource invalid.");
    }
    if (placeholderDrawable != null) {
      throw new IllegalStateException("Placeholder image already set.");
    }
    this.placeholderResId = placeholderResId;
    return this;
  }

  public RequestBuilder placeholder(Drawable placeholderDrawable) {
    if (placeholderDrawable == null) {
      throw new IllegalArgumentException("Placeholder image may not be null.");
    }
    if (placeholderResId != 0) {
      throw new IllegalStateException("Placeholder image already set.");
    }
    this.placeholderDrawable = placeholderDrawable;
    return this;
  }

  public RequestBuilder error(int errorResId) {
    if (errorResId == 0) {
      throw new IllegalArgumentException("Error image resource invalid.");
    }
    if (errorDrawable != null) {
      throw new IllegalStateException("Error image already set.");
    }
    this.errorResId = errorResId;
    return this;
  }

  public RequestBuilder error(Drawable errorDrawable) {
    if (errorDrawable == null) {
      throw new IllegalArgumentException("Error image may not be null.");
    }
    if (errorResId != 0) {
      throw new IllegalStateException("Error image already set.");
    }
    this.errorDrawable = errorDrawable;
    return this;
  }

  public RequestBuilder fit() {
    PicassoBitmapOptions options = getOptions();

    if (options.targetWidth != 0 || options.targetHeight != 0) {
      throw new IllegalStateException("Fit cannot be used with resize.");
    }

    options.deferredResize = true;
    return this;
  }

  public RequestBuilder resizeDimen(int targetWidthResId, int targetHeightResId) {
    Resources resources = picasso.context.getResources();
    int targetWidth = resources.getDimensionPixelSize(targetWidthResId);
    int targetHeight = resources.getDimensionPixelSize(targetHeightResId);
    return resize(targetWidth, targetHeight);
  }

  public RequestBuilder resize(int targetWidth, int targetHeight) {
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
    if (type != Request.Type.STREAM) {
      options.inJustDecodeBounds = true;
    }

    return this;
  }

  public RequestBuilder centerCrop() {
    PicassoBitmapOptions options = getOptions();

    if (options.targetWidth == 0 || options.targetHeight == 0) {
      throw new IllegalStateException("Center crop can only be used after calling resize.");
    }

    options.centerCrop = true;
    return this;
  }

  public RequestBuilder scale(float factor) {
    if (factor != 1) {
      scale(factor, factor);
    }
    return this;
  }

  public RequestBuilder scale(float factorX, float factorY) {
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

  public RequestBuilder rotate(float degrees) {
    if (degrees != 0) {
      PicassoBitmapOptions options = getOptions();
      options.targetRotation = degrees;
    }
    return this;
  }

  public RequestBuilder rotate(float degrees, float pivotX, float pivotY) {
    if (degrees != 0) {
      PicassoBitmapOptions pbo = getOptions();
      pbo.targetRotation = degrees;
      pbo.targetPivotX = pivotX;
      pbo.targetPivotY = pivotY;
      pbo.hasRotationPivot = true;
    }
    return this;
  }

  public RequestBuilder transform(Transformation transformation) {
    if (transformation == null) {
      throw new IllegalArgumentException("Transformation may not be null.");
    }
    if (transformations == null) {
      transformations = new ArrayList<Transformation>(2);
    }
    transformations.add(transformation);
    return this;
  }

  public Bitmap get() throws IOException {
    checkNotMain();
    Request request =
        new Request(picasso, path, resourceId, null, options, transformations, type, errorResId,
            errorDrawable);
    return picasso.resolveRequest(request);
  }

  /** Fills the target with the result of the request. */
  public void fetch(Target target) {
    makeTargetRequest(target, true);
  }

  /**
   * Keeps a {@link java.lang.ref.WeakReference} to the target. Fills the target with the
   * result of the request if it is still available.
   */
  public void into(Target target) {
    makeTargetRequest(target, false);
  }

  private void makeTargetRequest(Target target, boolean strong) {
    if (target == null) {
      throw new IllegalArgumentException("Target cannot be null.");
    }

    Bitmap bitmap = picasso.quickMemoryCacheCheck(target,
        createKey(path, resourceId, options, transformations));
    if (bitmap != null) {
      target.onSuccess(bitmap);
      return;
    }

    Request request =
        new TargetRequest(picasso, path, resourceId, target, strong, options, transformations, type,
            errorResId, errorDrawable);
    picasso.submit(request);
  }

  /**
   * Keeps a {@link java.lang.ref.WeakReference} to the view. Fills the view with the
   * result of the request if it is still available.
   */
  public void into(ImageView target) {
    if (target == null) {
      throw new IllegalArgumentException("Target cannot be null.");
    }

    Bitmap bitmap = picasso.quickMemoryCacheCheck(target,
        createKey(path, resourceId, options, transformations));
    if (bitmap != null) {
      target.setImageBitmap(bitmap);
      return;
    }

    if (placeholderDrawable != null) {
      target.setImageDrawable(placeholderDrawable);
    } else if (placeholderResId != 0) {
      target.setImageResource(placeholderResId);
    }

    Request request =
        new Request(picasso, path, resourceId, target, options, transformations, type, errorResId,
            errorDrawable);
    picasso.submit(request);
  }
}
