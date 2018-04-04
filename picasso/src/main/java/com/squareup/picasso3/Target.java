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

import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.widget.ImageView;

public class Target<T> {
  T target;
  final boolean setPlaceholder;
  final int placeholderResId;
  final Drawable placeholderDrawable;
  final int errorResId;
  final Drawable errorDrawable;
  final boolean noFade;

  Target(Builder<T> builder) {
    this.target = builder.target;
    this.setPlaceholder = builder.setPlaceholder;
    this.placeholderResId = builder.placeholderResId;
    this.placeholderDrawable = builder.placeholderDrawable;
    this.errorResId = builder.errorResId;
    this.errorDrawable = builder.errorDrawable;
    this.noFade = builder.noFade;
  }

  static class Builder<T> {
    private T target;
    private boolean setPlaceholder = true;
    private int placeholderResId;
    private Drawable placeholderDrawable;
    private int errorResId;
    private Drawable errorDrawable;
    boolean noFade;

    public Builder target(T target) {
      this.target = target;
      return this;
    }

    /**
     * Explicitly opt-out to having a placeholder set when calling {@code into}.
     * <p>
     * By default, Picasso will either set a supplied placeholder or clear the target
     * {@link ImageView} in order to ensure behavior in situations where views are recycled. This
     * method will prevent that behavior and retain any already set image.
     */
    public Builder noPlaceholder() {
      if (placeholderResId != 0) {
        throw new IllegalStateException("Placeholder resource already set.");
      }
      if (placeholderDrawable != null) {
        throw new IllegalStateException("Placeholder image already set.");
      }
      setPlaceholder = false;
      return this;
    }

    /**
     * A placeholder resource to be used while the image is being loaded. If the requested image is
     * not immediately available in the memory cache then this resource will be set on the target
     * {@link ImageView}.
     */
    public Builder placeholder(@DrawableRes int placeholderResId) {
      if (!setPlaceholder) {
        throw new IllegalStateException("Already explicitly declared as no placeholder.");
      }
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
     * not immediately available in the memory cache then this drawable will be set on the target
     * {@link ImageView}.
     * <p>
     * If you are not using a placeholder image but want to clear an existing image (such as when
     * used in an {@link android.widget.Adapter adapter}), pass in {@code null}.
     */
    public Builder placeholder(Drawable placeholderDrawable) {
      if (!setPlaceholder) {
        throw new IllegalStateException("Already explicitly declared as no placeholder.");
      }
      if (placeholderResId != 0) {
        throw new IllegalStateException("Placeholder image already set.");
      }
      this.placeholderDrawable = placeholderDrawable;
      return this;
    }

    /** An error resource to be used if the request image could not be loaded. */
    public Builder error(@DrawableRes int errorResId) {
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
    public Builder error(@NonNull Drawable errorDrawable) {
      if (errorDrawable == null) {
        throw new IllegalArgumentException("Error image may not be null.");
      }
      if (errorResId != 0) {
        throw new IllegalStateException("Error image already set.");
      }
      this.errorDrawable = errorDrawable;
      return this;
    }

    /** Disable brief fade in of images loaded from the disk cache or network. */
    public Builder noFade(boolean noFade) {
      this.noFade = noFade;
      return this;
    }

    public Target<T> build() {
      return new Target<>(this);
    }
  }
}
