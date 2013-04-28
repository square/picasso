package com.squareup.picasso;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.TestOnly;

import static com.squareup.picasso.Request.LoadedFrom.MEMORY;
import static com.squareup.picasso.Utils.checkNotMain;
import static com.squareup.picasso.Utils.createKey;

/** Fluent API for building an image download request. */
@SuppressWarnings("UnusedDeclaration") // Public API.
public class RequestBuilder {
  private final Picasso picasso;
  private final String path;
  private final int resourceId;
  private final Request.Type type;

  PicassoBitmapOptions options;
  private List<Transformation> transformations;
  private int placeholderResId;
  private int errorResId;
  private boolean skipCache;
  private Drawable placeholderDrawable;
  private Drawable errorDrawable;

  RequestBuilder(Picasso picasso, int resourceId) {
    this.picasso = picasso;
    this.path = null;
    this.resourceId = resourceId;
    this.type = Request.Type.RESOURCE;
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

  /**
   * A placeholder drawable to be used while the image is being loaded. If the requested image is
   * not immediately available in the memory cache then this resource will be set on the target
   * {@link ImageView}.
   */
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

  /**
   * A placeholder drawable to be used while the image is being loaded. If the requested image is
   * not immediately available in the memory cache then this resource will be set on the target
   * {@link ImageView}.
   */
  public RequestBuilder placeholder(Drawable placeholderDrawable) {
    if (placeholderDrawable == null) {
      throw new IllegalArgumentException("Placeholder image must not be null.");
    }
    if (placeholderResId != 0) {
      throw new IllegalStateException("Placeholder image already set.");
    }
    this.placeholderDrawable = placeholderDrawable;
    return this;
  }

  /** An error drawable to be used if the request image could not be loaded. */
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

  /** An error drawable to be used if the request image could not be loaded. */
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

  /**
   * Attempt to resize the image to fit exactly into the target {@link ImageView}'s bounds. This
   * will only have an affect if the target view has been measured when the image becomes
   * available.
   */
  public RequestBuilder fit() {
    PicassoBitmapOptions options = getOptions();

    if (options.targetWidth != 0 || options.targetHeight != 0) {
      throw new IllegalStateException("Fit cannot be used with resize.");
    }

    options.deferredResize = true;
    return this;
  }

  /** Resize the image to the specified dimension size. */
  public RequestBuilder resizeDimen(int targetWidthResId, int targetHeightResId) {
    Resources resources = picasso.context.getResources();
    int targetWidth = resources.getDimensionPixelSize(targetWidthResId);
    int targetHeight = resources.getDimensionPixelSize(targetHeightResId);
    return resize(targetWidth, targetHeight);
  }

  /** Resize the image to the specified size in pixels. */
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

  /**
   * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
   * distorting the aspect ratio. This cropping technique scales the image so that it fills the
   * requested bounds and then crops the extra.
   */
  public RequestBuilder centerCrop() {
    PicassoBitmapOptions options = getOptions();

    if (options.targetWidth == 0 || options.targetHeight == 0) {
      throw new IllegalStateException("Center crop can only be used after calling resize.");
    }

    options.centerCrop = true;
    return this;
  }

  /** Scale the image using the specified factor. */
  public RequestBuilder scale(float factor) {
    if (factor != 1) {
      scale(factor, factor);
    }
    return this;
  }

  /** Scale the image using the specified factors. */
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

  /** Rotate the image by the specified degrees. */
  public RequestBuilder rotate(float degrees) {
    if (degrees != 0) {
      PicassoBitmapOptions options = getOptions();
      options.targetRotation = degrees;
    }
    return this;
  }

  /** Rotate the image by the specified degrees around a pivot point. */
  public RequestBuilder rotate(float degrees, float pivotX, float pivotY) {
    if (degrees != 0) {
      PicassoBitmapOptions options = getOptions();
      options.targetRotation = degrees;
      options.targetPivotX = pivotX;
      options.targetPivotY = pivotY;
      options.hasRotationPivot = true;
    }
    return this;
  }

  /**
   * Add a custom transformation to be applied to the image.
   * <p/>
   * Custom transformations will always be run after the built-in transformations.
   */
  // TODO show example of calling resize after a transform in the javadoc
  public RequestBuilder transform(Transformation transformation) {
    if (transformation == null) {
      throw new IllegalArgumentException("Transformation must not be null.");
    }
    if (transformations == null) {
      transformations = new ArrayList<Transformation>(2);
    }
    transformations.add(transformation);
    return this;
  }

  /**
   * Indicate that this request should not use the memory cache for attempting to load or save the
   * image. This is useful for when you know an image will only ever be used once (e.g., loading
   * an image from the filesystem and uploading to a remote server).
   */
  public RequestBuilder skipCache() {
    skipCache = true;
    return this;
  }

  /** Synchronously fulfill this request. Must not be called from the main thread. */
  public Bitmap get() throws IOException {
    checkNotMain();
    Request request =
        new Request(picasso, path, resourceId, null, options, transformations, type, skipCache,
            errorResId, errorDrawable);
    return picasso.resolveRequest(request);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link Target}.
   * <p/>
   * <em>Note:</em> This method keeps a strong reference to the {@link Target} instance.
   */
  public void fetch(Target target) {
    makeTargetRequest(target, true);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link Target}.
   * <p/>
   * <em>Note:</em> This method keeps a weak reference to the {@link Target} instance and will
   * automatically support object recycling.
   */
  public void into(Target target) {
    makeTargetRequest(target, false);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link ImageView}.
   * <p/>
   * This method keeps a weak reference to the view and will automatically support object
   * recycling.
   */
  public void into(ImageView target) {
    if (target == null) {
      throw new IllegalArgumentException("Target must not be null.");
    }

    Bitmap bitmap = picasso.quickMemoryCacheCheck(target,
        createKey(path, resourceId, options, transformations));
    if (bitmap != null) {
      Resources res = picasso.context.getResources();
      target.setImageDrawable(new PicassoDrawable(res, bitmap, picasso.debugging, MEMORY));
      return;
    }

    if (placeholderDrawable != null) {
      target.setImageDrawable(placeholderDrawable);
    } else if (placeholderResId != 0) {
      target.setImageResource(placeholderResId);
    }

    Request request =
        new Request(picasso, path, resourceId, target, options, transformations, type, skipCache,
            errorResId, errorDrawable);
    picasso.submit(request);
  }

  private void makeTargetRequest(Target target, boolean strong) {
    if (target == null) {
      throw new IllegalArgumentException("Target must not be null.");
    }

    Bitmap bitmap = picasso.quickMemoryCacheCheck(target,
        createKey(path, resourceId, options, transformations));
    if (bitmap != null) {
      target.onSuccess(bitmap);
      return;
    }

    Request request =
        new TargetRequest(picasso, path, resourceId, target, strong, options, transformations, type,
            skipCache, errorResId, errorDrawable);
    picasso.submit(request);
  }
}
