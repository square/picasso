package com.squareup.picasso;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

/** Immutable data about an image and the transformations that will be applied to it. */
public final class Request {
  /**
   * The image URI.
   * <p>
   * This is mutually exclusive with {@link #resourceId}.
   */
  public final Uri uri;
  /**
   * The image resource ID.
   * <p>
   * This is mutually exclusive with {@link #uri}.
   */
  public final int resourceId;
  /** List of custom transformations to be applied after the built-in transformations. */
  public final List<Transformation> transformations;
  /** Target image width for resizing. */
  public final int targetWidth;
  /** Target image height for resizing. */
  public final int targetHeight;
  /**
   * True if the final image should use the 'centerCrop' scale technique.
   * <p>
   * This is mutually exclusive with {@link #centerInside}.
   */
  public final boolean centerCrop;
  /**
   * True if the final image should use the 'centerInside' scale technique.
   * <p>
   * This is mutually exclusive with {@link #centerCrop}.
   */
  public final boolean centerInside;
  /** Amount to rotate the image in degrees. */
  public final float rotationDegrees;
  /** Rotation pivot on the X axis. */
  public final float rotationPivotX;
  /** Rotation pivot on the Y axis. */
  public final float rotationPivotY;
  /** Whether or not {@link #rotationPivotX} and {@link #rotationPivotY} are set. */
  public final boolean hasRotationPivot;

  private Request(Uri uri, int resourceId, List<Transformation> transformations, int targetWidth,
      int targetHeight, boolean centerCrop, boolean centerInside, float rotationDegrees,
      float rotationPivotX, float rotationPivotY, boolean hasRotationPivot) {
    this.uri = uri;
    this.resourceId = resourceId;
    if (transformations == null) {
      this.transformations = null;
    } else {
      this.transformations = unmodifiableList(transformations);
    }
    this.targetWidth = targetWidth;
    this.targetHeight = targetHeight;
    this.centerCrop = centerCrop;
    this.centerInside = centerInside;
    this.rotationDegrees = rotationDegrees;
    this.rotationPivotX = rotationPivotX;
    this.rotationPivotY = rotationPivotY;
    this.hasRotationPivot = hasRotationPivot;
  }

  String getName() {
    if (uri != null) {
      return uri.getPath();
    }
    return Integer.toHexString(resourceId);
  }

  boolean hasSize() {
    return targetWidth != 0;
  }

  boolean needsTransformation() {
    return needsMatrixTransform() || hasCustomTransformations();
  }

  boolean needsMatrixTransform() {
    return targetWidth != 0 || rotationDegrees != 0;
  }

  boolean hasCustomTransformations() {
    return transformations != null;
  }

  /** Builder for creating {@link Request} instances. */
  public static final class Builder {
    private final Uri uri;
    private final int resourceId;
    private int targetWidth;
    private int targetHeight;
    private boolean centerCrop;
    private boolean centerInside;
    private float rotationDegrees;
    private float rotationPivotX;
    private float rotationPivotY;
    private boolean hasRotationPivot;
    private List<Transformation> transformations;

    /** Start building a request using the specified {@link Uri}. */
    public Builder(Uri uri) {
      if (uri == null) {
        throw new IllegalArgumentException("Image URI may not be null.");
      }
      this.uri = uri;
      this.resourceId = 0;
    }

    /** Start building a request using the specified resource ID. */
    public Builder(int resourceId) {
      if (resourceId == 0) {
        throw new IllegalArgumentException("Image ID may not be 0.");
      }
      this.uri = null;
      this.resourceId = resourceId;
    }

    Builder(Uri uri, int resourceId) {
      this.uri = uri;
      this.resourceId = resourceId;
    }

    boolean hasImage() {
      return uri != null || resourceId != 0;
    }

    boolean hasSize() {
      return targetWidth != 0;
    }

    /** Resize the image to the specified size in pixels. */
    public Builder resize(int targetWidth, int targetHeight) {
      if (targetWidth <= 0) {
        throw new IllegalArgumentException("Width must be positive number.");
      }
      if (targetHeight <= 0) {
        throw new IllegalArgumentException("Height must be positive number.");
      }
      this.targetWidth = targetWidth;
      this.targetHeight = targetHeight;
      return this;
    }

    /**
     * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
     * distorting the aspect ratio. This cropping technique scales the image so that it fills the
     * requested bounds and then crops the extra.
     */
    public Builder centerCrop() {
      if (targetWidth == 0 || targetHeight == 0) {
        throw new IllegalStateException("Center crop can only be used after calling resize.");
      }
      if (centerInside) {
        throw new IllegalStateException("Center crop can not be used after calling centerInside");
      }
      centerCrop = true;
      return this;
    }

    /**
     * Centers an image inside of the bounds specified by {@link #resize(int, int)}. This scales
     * the image so that both dimensions are equal to or less than the requested bounds.
     */
    public Builder centerInside() {
      if (targetWidth == 0 || targetHeight == 0) {
        throw new IllegalStateException("Center inside can only be used after calling resize.");
      }
      if (centerCrop) {
        throw new IllegalStateException("Center inside can not be used after calling centerCrop");
      }
      centerInside = true;
      return this;
    }

    /** Rotate the image by the specified degrees. */
    public Builder rotate(float degrees) {
      rotationDegrees = degrees;
      return this;
    }

    /** Rotate the image by the specified degrees around a pivot point. */
    public Builder rotate(float degrees, float pivotX, float pivotY) {
      rotationDegrees = degrees;
      rotationPivotX = pivotX;
      rotationPivotY = pivotY;
      hasRotationPivot = true;
      return this;
    }

    /**
     * Add a custom transformation to be applied to the image.
     * <p/>
     * Custom transformations will always be run after the built-in transformations.
     */
    public Builder transform(Transformation transformation) {
      if (transformation == null) {
        throw new IllegalArgumentException("Transformation must not be null.");
      }
      if (transformations == null) {
        transformations = new ArrayList<Transformation>(2);
      }
      transformations.add(transformation);
      return this;
    }

    /** Create the immutable {@link Request} object. */
    public Request build() {
      if (centerInside && centerCrop) {
        throw new IllegalStateException("Center crop and center inside can not be used together.");
      }
      if (centerCrop && targetWidth == 0) {
        throw new IllegalStateException("Center crop requires calling resize.");
      }
      if (centerInside && targetWidth == 0) {
        throw new IllegalStateException("Center inside requires calling resize.");
      }
      return new Request(uri, resourceId, transformations, targetWidth, targetHeight, centerCrop,
          centerInside, rotationDegrees, rotationPivotX, rotationPivotY, hasRotationPivot);
    }
  }
}
