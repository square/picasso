/*
 * Copyright (C) 2014 Square, Inc.
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
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.io.IOException;

import static com.squareup.picasso3.Utils.checkNotNull;

/**
 * {@code RequestHandler} allows you to extend Picasso to load images in ways that are not
 * supported by default in the library.
 * <p>
 * <h2>Usage</h2>
 * {@code RequestHandler} must be subclassed to be used. You will have to override two methods
 * ({@link #canHandleRequest(Request)} and {@link #load(Picasso, Request, Callback)}) with
 * your custom logic to load images.
 * <p>
 * You should then register your {@link RequestHandler} using
 * {@link Picasso.Builder#addRequestHandler(RequestHandler)}
 * <p>
 * <b>Note:</b> This is a beta feature. The API is subject to change in a backwards incompatible
 * way at any time.
 *
 * @see Picasso.Builder#addRequestHandler(RequestHandler)
 */
public abstract class RequestHandler {
  /**
   * {@link Result} represents the result of a {@link #load(Picasso, Request, Callback)} call
   * in a {@link RequestHandler}.
   *
   * @see RequestHandler
   * @see #load(Picasso, Request, Callback)
   */
  public static final class Result {
    private final Picasso.LoadedFrom loadedFrom;
    @Nullable private final Bitmap bitmap;
    @Nullable private final Drawable drawable;
    private final int exifRotation;

    public Result(@NonNull Bitmap bitmap, @NonNull Picasso.LoadedFrom loadedFrom) {
      this(checkNotNull(bitmap, "bitmap == null"), null, loadedFrom, 0);
    }

    public Result(@NonNull Bitmap bitmap, @NonNull Picasso.LoadedFrom loadedFrom,
        int exifRotation) {
      this(checkNotNull(bitmap, "bitmap == null"), null, loadedFrom, exifRotation);
    }

    public Result(@NonNull Drawable drawable, @NonNull Picasso.LoadedFrom loadedFrom) {
      this(null, checkNotNull(drawable, "drawable == null"), loadedFrom, 0);
    }

    private Result(
        @Nullable Bitmap bitmap,
        @Nullable Drawable drawable,
        @NonNull Picasso.LoadedFrom loadedFrom,
        int exifRotation) {
      this.bitmap = bitmap;
      this.drawable = drawable;
      this.loadedFrom = checkNotNull(loadedFrom, "loadedFrom == null");
      this.exifRotation = exifRotation;
    }

    /**
     * The loaded {@link Bitmap}.
     * Mutually exclusive with {@link #getDrawable()}.
     */
    @Nullable public Bitmap getBitmap() {
      return bitmap;
    }

    /**
     * The loaded {@link Drawable}.
     * Mutually exclusive with {@link #getBitmap()}.
     */
    @Nullable public Drawable getDrawable() {
      return drawable;
    }

    /**
     * Returns the resulting {@link Picasso.LoadedFrom} generated from a
     * {@link #load(Picasso, Request, Callback)} call.
     */
    @NonNull public Picasso.LoadedFrom getLoadedFrom() {
      return loadedFrom;
    }

    /**
     * Returns the resulting EXIF rotation generated from a
     * {@link #load(Picasso, Request, Callback)} call.
     */
    public int getExifRotation() {
      return exifRotation;
    }
  }

  public interface Callback {
    void onSuccess(@Nullable Result result);

    void onError(@NonNull Throwable t);
  }

  /**
   * Whether or not this {@link RequestHandler} can handle a request with the given {@link Request}.
   */
  public abstract boolean canHandleRequest(@NonNull Request data);

  /**
   * Loads an image for the given {@link Request}.
   * @param request the data from which the image should be resolved.
   */
  public abstract void load(@NonNull Picasso picasso, @NonNull Request request,
      @NonNull Callback callback) throws IOException;

  int getRetryCount() {
    return 0;
  }

  boolean shouldRetry(boolean airplaneMode, @Nullable NetworkInfo info) {
    return false;
  }

  boolean supportsReplay() {
    return false;
  }
}
