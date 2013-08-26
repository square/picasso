/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public boolean hasSize() {
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

  public Builder buildUpon() {
    return new Builder(this);
  }

  /** Builder for creating {@link Request} instances. */
  public static final class Builder {
    private Uri uri;
    private int resourceId;
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
      setUri(uri);
    }

    /** Start building a request using the specified resource ID. */
    public Builder(int resourceId) {
      setResourceId(resourceId);
    }

    Builder(Uri uri, int resourceId) {
      this.uri = uri;
      this.resourceId = resourceId;
    }

    private Builder(Request request) {
      uri = request.uri;
      resourceId = request.resourceId;
      targetWidth = request.targetWidth;
      targetHeight = request.targetHeight;
      centerCrop = request.centerCrop;
      centerInside = request.centerInside;
      rotationDegrees = request.rotationDegrees;
      rotationPivotX = request.rotationPivotX;
      rotationPivotY = request.rotationPivotY;
      hasRotationPivot = request.hasRotationPivot;
      if (request.transformations != null) {
        transformations = new ArrayList<Transformation>(request.transformations);
      }
    }

    boolean hasImage() {
      return uri != null || resourceId != 0;
    }

    boolean hasSize() {
      return targetWidth != 0;
    }

    /**
     * Set the target image Uri.
     * <p>
     * This will clear an image resource ID if one is set.
     */
    public Builder setUri(Uri uri) {
      if (uri == null) {
        throw new IllegalArgumentException("Image URI may not be null.");
      }
      this.uri = uri;
      this.resourceId = 0;
      return this;
    }

    /**
     * Set the target image resource ID.
     * <p>
     * This will clear an image Uri if one is set.
     */
    public Builder setResourceId(int resourceId) {
      if (resourceId == 0) {
        throw new IllegalArgumentException("Image resource ID may not be 0.");
      }
      this.resourceId = resourceId;
      this.uri = null;
      return this;
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

    /** Clear the resize transformation, if any. This will also clear center crop/inside if set. */
    public Builder clearResize() {
      targetWidth = 0;
      targetHeight = 0;
      centerCrop = false;
      centerInside = false;
      return this;
    }

    /**
     * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
     * distorting the aspect ratio. This cropping technique scales the image so that it fills the
     * requested bounds and then crops the extra.
     */
    public Builder centerCrop() {
      if (centerInside) {
        throw new IllegalStateException("Center crop can not be used after calling centerInside");
      }
      centerCrop = true;
      return this;
    }

    /** Clear the center crop transformation flag, if set. */
    public Builder clearCenterCrop() {
      centerCrop = false;
      return this;
    }

    /**
     * Centers an image inside of the bounds specified by {@link #resize(int, int)}. This scales
     * the image so that both dimensions are equal to or less than the requested bounds.
     */
    public Builder centerInside() {
      if (centerCrop) {
        throw new IllegalStateException("Center inside can not be used after calling centerCrop");
      }
      centerInside = true;
      return this;
    }

    /** Clear the center inside transformation flag, if set. */
    public Builder clearCenterInside() {
      centerInside = false;
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

    /** Clear the rotation transformation, if any. */
    public Builder clearRotation() {
      rotationDegrees = 0;
      rotationPivotX = 0;
      rotationPivotY = 0;
      hasRotationPivot = false;
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
