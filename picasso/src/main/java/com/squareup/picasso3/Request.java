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
package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Looper;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.view.Gravity;
import com.squareup.picasso3.Picasso.Priority;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.squareup.picasso3.Utils.MAIN_THREAD_KEY_BUILDER;
import static com.squareup.picasso3.Utils.checkNotNull;

/** Immutable data about an image and the transformations that will be applied to it. */
public final class Request {
  private static final long TOO_LONG_LOG = TimeUnit.SECONDS.toNanos(5);
  private static final int KEY_PADDING = 50; // Determined by exact science.
  static final char KEY_SEPARATOR = '\n';

  /** A unique ID for the request. */
  int id;
  /** The time that the request was first submitted (in nanos). */
  long started;
  /** The {@link MemoryPolicy} to use for this request. */
  final int memoryPolicy;
  /** The {@link NetworkPolicy} to use for this request. */
  final int networkPolicy;

  /**
   * The image URI.
   * <p>
   * This is mutually exclusive with {@link #resourceId}.
   */
  @Nullable
  public final Uri uri;
  /**
   * The image resource ID.
   * <p>
   * This is mutually exclusive with {@link #uri}.
   */
  public final int resourceId;
  /**
   * Optional stable key for this request to be used instead of the URI or resource ID when
   * caching. Two requests with the same value are considered to be for the same resource.
   */
  @Nullable
  public final String stableKey;
  /** List of custom transformations to be applied after the built-in transformations. */
  final List<Transformation> transformations;
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
  /** If centerCrop is set, controls alignment of centered image */
  public final int centerCropGravity;
  /**
   * True if the final image should use the 'centerInside' scale technique.
   * <p>
   * This is mutually exclusive with {@link #centerCrop}.
   */
  public final boolean centerInside;
  public final boolean onlyScaleDown;
  /** Amount to rotate the image in degrees. */
  public final float rotationDegrees;
  /** Rotation pivot on the X axis. */
  public final float rotationPivotX;
  /** Rotation pivot on the Y axis. */
  public final float rotationPivotY;
  /** Whether or not {@link #rotationPivotX} and {@link #rotationPivotY} are set. */
  public final boolean hasRotationPivot;
  /** True if image should be decoded with inPurgeable and inInputShareable. */
  public final boolean purgeable;
  /** Target image config for decoding. */
  @Nullable
  public final Bitmap.Config config;
  /** The priority of this request. */
  @NonNull
  public final Priority priority;
  /** The cache key for this request. */
  @NonNull
  public final String key;
  /** User-provided value to track this request. */
  @Nullable
  public final Object tag;

  Request(Builder builder) {
    this.uri = builder.uri;
    this.resourceId = builder.resourceId;
    this.stableKey = builder.stableKey;
    if (builder.transformations == null) {
      this.transformations = Collections.emptyList();
    } else {
      this.transformations = Collections.unmodifiableList(new ArrayList<>(builder.transformations));
    }
    this.targetWidth = builder.targetWidth;
    this.targetHeight = builder.targetHeight;
    this.centerCrop = builder.centerCrop;
    this.centerInside = builder.centerInside;
    this.centerCropGravity = builder.centerCropGravity;
    this.onlyScaleDown = builder.onlyScaleDown;
    this.rotationDegrees = builder.rotationDegrees;
    this.rotationPivotX = builder.rotationPivotX;
    this.rotationPivotY = builder.rotationPivotY;
    this.hasRotationPivot = builder.hasRotationPivot;
    this.purgeable = builder.purgeable;
    this.config = builder.config;
    this.priority = checkNotNull(builder.priority, "priority == null");

    if (Looper.myLooper() == Looper.getMainLooper()) {
      this.key = createKey();
    } else {
      this.key = createKey(new StringBuilder());
    }

    this.tag = builder.tag;
    this.memoryPolicy = builder.memoryPolicy;
    this.networkPolicy = builder.networkPolicy;
  }

  @NonNull
  @Override public String toString() {
    final StringBuilder builder = new StringBuilder("Request{");
    if (resourceId > 0) {
      builder.append(resourceId);
    } else {
      builder.append(uri);
    }
    if (!transformations.isEmpty()) {
      for (Transformation transformation : transformations) {
        builder.append(' ').append(transformation.key());
      }
    }
    if (stableKey != null) {
      builder.append(" stableKey(").append(stableKey).append(')');
    }
    if (targetWidth > 0) {
      builder.append(" resize(").append(targetWidth).append(',').append(targetHeight).append(')');
    }
    if (centerCrop) {
      builder.append(" centerCrop");
    }
    if (centerInside) {
      builder.append(" centerInside");
    }
    if (rotationDegrees != 0) {
      builder.append(" rotation(").append(rotationDegrees);
      if (hasRotationPivot) {
        builder.append(" @ ").append(rotationPivotX).append(',').append(rotationPivotY);
      }
      builder.append(')');
    }
    if (purgeable) {
      builder.append(" purgeable");
    }
    if (config != null) {
      builder.append(' ').append(config);
    }
    builder.append('}');

    return builder.toString();
  }

  String logId() {
    long delta = System.nanoTime() - started;
    if (delta > TOO_LONG_LOG) {
      return plainId() + '+' + TimeUnit.NANOSECONDS.toSeconds(delta) + 's';
    }
    return plainId() + '+' + TimeUnit.NANOSECONDS.toMillis(delta) + "ms";
  }

  String plainId() {
    return "[R" + id + ']';
  }

  String getName() {
    if (uri != null) {
      return String.valueOf(uri.getPath());
    }
    return Integer.toHexString(resourceId);
  }

  public boolean hasSize() {
    return targetWidth != 0 || targetHeight != 0;
  }

  boolean needsMatrixTransform() {
    return hasSize() || rotationDegrees != 0;
  }

  @NonNull
  public Builder newBuilder() {
    return new Builder(this);
  }

  private String createKey() {
    String result = createKey(MAIN_THREAD_KEY_BUILDER);
    MAIN_THREAD_KEY_BUILDER.setLength(0);
    return result;
  }

  private String createKey(StringBuilder builder) {
    Request data = this;
    if (data.stableKey != null) {
      builder.ensureCapacity(data.stableKey.length() + KEY_PADDING);
      builder.append(data.stableKey);
    } else if (data.uri != null) {
      String path = data.uri.toString();
      builder.ensureCapacity(path.length() + KEY_PADDING);
      builder.append(path);
    } else {
      builder.ensureCapacity(KEY_PADDING);
      builder.append(data.resourceId);
    }
    builder.append(KEY_SEPARATOR);

    if (data.rotationDegrees != 0) {
      builder.append("rotation:").append(data.rotationDegrees);
      if (data.hasRotationPivot) {
        builder.append('@').append(data.rotationPivotX).append('x').append(data.rotationPivotY);
      }
      builder.append(KEY_SEPARATOR);
    }
    if (data.hasSize()) {
      builder.append("resize:").append(data.targetWidth).append('x').append(data.targetHeight);
      builder.append(KEY_SEPARATOR);
    }
    if (data.centerCrop) {
      builder.append("centerCrop:").append(data.centerCropGravity).append(KEY_SEPARATOR);
    } else if (data.centerInside) {
      builder.append("centerInside").append(KEY_SEPARATOR);
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, count = data.transformations.size(); i < count; i++) {
      builder.append(data.transformations.get(i).key());
      builder.append(KEY_SEPARATOR);
    }

    return builder.toString();
  }

  /** Builder for creating {@link Request} instances. */
  public static final class Builder {
    @Nullable Uri uri;
    int resourceId;
    @Nullable String stableKey;
    int targetWidth;
    int targetHeight;
    boolean centerCrop;
    int centerCropGravity;
    boolean centerInside;
    boolean onlyScaleDown;
    float rotationDegrees;
    float rotationPivotX;
    float rotationPivotY;
    boolean hasRotationPivot;
    boolean purgeable;
    @Nullable List<Transformation> transformations;
    @Nullable Bitmap.Config config;
    @Nullable Priority priority;
    @Nullable Object tag;
    int memoryPolicy;
    int networkPolicy;

    /** Start building a request using the specified {@link Uri}. */
    public Builder(@NonNull Uri uri) {
      setUri(uri);
    }

    /** Start building a request using the specified resource ID. */
    public Builder(@DrawableRes int resourceId) {
      setResourceId(resourceId);
    }

    Builder(@Nullable Uri uri, int resourceId, @Nullable Bitmap.Config bitmapConfig) {
      this.uri = uri;
      this.resourceId = resourceId;
      this.config = bitmapConfig;
    }

    Builder(Request request) {
      uri = request.uri;
      resourceId = request.resourceId;
      stableKey = request.stableKey;
      targetWidth = request.targetWidth;
      targetHeight = request.targetHeight;
      centerCrop = request.centerCrop;
      centerInside = request.centerInside;
      centerCropGravity = request.centerCropGravity;
      rotationDegrees = request.rotationDegrees;
      rotationPivotX = request.rotationPivotX;
      rotationPivotY = request.rotationPivotY;
      hasRotationPivot = request.hasRotationPivot;
      purgeable = request.purgeable;
      onlyScaleDown = request.onlyScaleDown;
      if (request.transformations != null) {
        transformations = new ArrayList<>(request.transformations);
      }
      config = request.config;
      priority = request.priority;
      memoryPolicy = request.memoryPolicy;
      networkPolicy = request.networkPolicy;
    }

    boolean hasImage() {
      return uri != null || resourceId != 0;
    }

    boolean hasSize() {
      return targetWidth != 0 || targetHeight != 0;
    }

    boolean hasPriority() {
      return priority != null;
    }

    /**
     * Set the target image Uri.
     * <p>
     * This will clear an image resource ID if one is set.
     */
    @NonNull
    public Builder setUri(@NonNull Uri uri) {
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
    @NonNull
    public Builder setResourceId(@DrawableRes int resourceId) {
      if (resourceId == 0) {
        throw new IllegalArgumentException("Image resource ID may not be 0.");
      }
      this.resourceId = resourceId;
      this.uri = null;
      return this;
    }

    /**
     * Set the stable key to be used instead of the URI or resource ID when caching.
     * Two requests with the same value are considered to be for the same resource.
     */
    @NonNull
    public Builder stableKey(@Nullable String stableKey) {
      this.stableKey = stableKey;
      return this;
    }

    /**
     * Assign a tag to this request.
     */
    @NonNull
    public Builder tag(@NonNull Object tag) {
      if (tag == null) {
        throw new IllegalArgumentException("Tag invalid.");
      }
      if (this.tag != null) {
        throw new IllegalStateException("Tag already set.");
      }
      this.tag = tag;
      return this;
    }

    /** Internal use only. Used by {@link DeferredRequestCreator}. */
    Builder clearTag() {
      this.tag = null;
      return this;
    }

    /** Internal use only. Used by {@link DeferredRequestCreator}. */
    @Nullable Object getTag() {
      return tag;
    }

    /**
     * Resize the image to the specified size in pixels.
     * Use 0 as desired dimension to resize keeping aspect ratio.
     */
    @NonNull
    public Builder resize(@Px int targetWidth, @Px int targetHeight) {
      if (targetWidth < 0) {
        throw new IllegalArgumentException("Width must be positive number or 0.");
      }
      if (targetHeight < 0) {
        throw new IllegalArgumentException("Height must be positive number or 0.");
      }
      if (targetHeight == 0 && targetWidth == 0) {
        throw new IllegalArgumentException("At least one dimension has to be positive number.");
      }
      this.targetWidth = targetWidth;
      this.targetHeight = targetHeight;
      return this;
    }

    /** Clear the resize transformation, if any. This will also clear center crop/inside if set. */
    @NonNull
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
    @NonNull
    public Builder centerCrop() {
      return centerCrop(Gravity.CENTER);
    }

    /**
     * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
     * distorting the aspect ratio. This cropping technique scales the image so that it fills the
     * requested bounds, aligns it using provided gravity parameter and then crops the extra.
     */
    @NonNull
    public Builder centerCrop(int alignGravity) {
      if (centerInside) {
        throw new IllegalStateException("Center crop can not be used after calling centerInside");
      }
      centerCrop = true;
      centerCropGravity = alignGravity;
      return this;
    }

    /** Clear the center crop transformation flag, if set. */
    @NonNull
    public Builder clearCenterCrop() {
      centerCrop = false;
      centerCropGravity = Gravity.CENTER;
      return this;
    }

    /**
     * Centers an image inside of the bounds specified by {@link #resize(int, int)}. This scales
     * the image so that both dimensions are equal to or less than the requested bounds.
     */
    @NonNull
    public Builder centerInside() {
      if (centerCrop) {
        throw new IllegalStateException("Center inside can not be used after calling centerCrop");
      }
      centerInside = true;
      return this;
    }

    /** Clear the center inside transformation flag, if set. */
    @NonNull
    public Builder clearCenterInside() {
      centerInside = false;
      return this;
    }

    /**
     * Only resize an image if the original image size is bigger than the target size
     * specified by {@link #resize(int, int)}.
     */
    @NonNull
    public Builder onlyScaleDown() {
      if (targetHeight == 0 && targetWidth == 0) {
        throw new IllegalStateException("onlyScaleDown can not be applied without resize");
      }
      onlyScaleDown = true;
      return this;
    }

    /** Clear the onlyScaleUp flag, if set. **/
    @NonNull
    public Builder clearOnlyScaleDown() {
      onlyScaleDown = false;
      return this;
    }

    /** Rotate the image by the specified degrees. */
    @NonNull
    public Builder rotate(float degrees) {
      rotationDegrees = degrees;
      return this;
    }

    /** Rotate the image by the specified degrees around a pivot point. */
    @NonNull
    public Builder rotate(float degrees, float pivotX, float pivotY) {
      rotationDegrees = degrees;
      rotationPivotX = pivotX;
      rotationPivotY = pivotY;
      hasRotationPivot = true;
      return this;
    }

    /** Clear the rotation transformation, if any. */
    @NonNull
    public Builder clearRotation() {
      rotationDegrees = 0;
      rotationPivotX = 0;
      rotationPivotY = 0;
      hasRotationPivot = false;
      return this;
    }

    @NonNull
    public Builder purgeable() {
      purgeable = true;
      return this;
    }

    /** Decode the image using the specified config. */
    @NonNull
    public Builder config(@NonNull Bitmap.Config config) {
      checkNotNull(config, "config == null");
      this.config = config;
      return this;
    }

    /** Execute request using the specified priority. */
    @NonNull
    public Builder priority(@NonNull Priority priority) {
      checkNotNull(priority, "priority == null");
      if (this.priority != null) {
        throw new IllegalStateException("Priority already set.");
      }
      this.priority = priority;
      return this;
    }

    /**
     * Add a custom transformation to be applied to the image.
     * <p>
     * Custom transformations will always be run after the built-in transformations.
     */
    @NonNull
    public Builder transform(@NonNull Transformation transformation) {
      checkNotNull(transformation, "transformation == null");
      if (transformation.key() == null) {
        throw new IllegalArgumentException("Transformation key must not be null.");
      }
      if (transformations == null) {
        transformations = new ArrayList<>(2);
      }
      transformations.add(transformation);
      return this;
    }

    /**
     * Add a list of custom transformations to be applied to the image.
     * <p>
     * Custom transformations will always be run after the built-in transformations.
     */
    @NonNull
    public Builder transform(@NonNull List<? extends Transformation> transformations) {
      checkNotNull(transformations, "transformations == null");
      for (int i = 0, size = transformations.size(); i < size; i++) {
        transform(transformations.get(i));
      }
      return this;
    }

    /**
     * Specifies the {@link MemoryPolicy} to use for this request. You may specify additional policy
     * options using the varargs parameter.
     */
    @NonNull
    public Builder memoryPolicy(@NonNull MemoryPolicy policy,
        @NonNull MemoryPolicy... additional) {
      if (policy == null) {
        throw new NullPointerException("policy == null");
      }
      this.memoryPolicy |= policy.index;
      if (additional == null) {
        throw new NullPointerException("additional == null");
      }
      for (MemoryPolicy memoryPolicy : additional) {
        if (memoryPolicy == null) {
          throw new NullPointerException("additional[i] == null");
        }
        this.memoryPolicy |= memoryPolicy.index;
      }
      return this;
    }

    /**
     * Specifies the {@link NetworkPolicy} to use for this request. You may specify additional
     * policy options using the varargs parameter.
     */
    @NonNull
    public Builder networkPolicy(@NonNull NetworkPolicy policy,
        @NonNull NetworkPolicy... additional) {
      if (policy == null) {
        throw new NullPointerException("policy == null");
      }
      this.networkPolicy |= policy.index;
      if (additional == null) {
        throw new NullPointerException("additional == null");
      }
      for (NetworkPolicy networkPolicy : additional) {
        if (networkPolicy == null) {
          throw new NullPointerException("additional[i] == null");
        }
        this.networkPolicy |= networkPolicy.index;
      }
      return this;
    }

    /** Create the immutable {@link Request} object. */
    @NonNull
    public Request build() {
      if (centerInside && centerCrop) {
        throw new IllegalStateException("Center crop and center inside can not be used together.");
      }
      if (centerCrop && (targetWidth == 0 && targetHeight == 0)) {
        throw new IllegalStateException(
            "Center crop requires calling resize with positive width and height.");
      }
      if (centerInside && (targetWidth == 0 && targetHeight == 0)) {
        throw new IllegalStateException(
            "Center inside requires calling resize with positive width and height.");
      }
      if (priority == null) {
        priority = Priority.NORMAL;
      }

      return new Request(this);
    }
  }
}
