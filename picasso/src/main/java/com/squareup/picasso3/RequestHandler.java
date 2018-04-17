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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.TypedValue;
import java.io.IOException;
import java.io.InputStream;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

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
    private final Bitmap bitmap;
    private final Drawable drawable;
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

    public boolean hasBitmap() {
      return bitmap != null;
    }

    /**
     * The loaded {@link Drawable}.
     * Mutually exclusive with {@link #getBitmap()}.
     */
    @Nullable public Drawable getDrawable() {
      return drawable;
    }

    public boolean hasDrawable() {
      return drawable != null;
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

  /**
   * Lazily create {@link BitmapFactory.Options} based in given
   * {@link Request}, only instantiating them if needed.
   */
  static BitmapFactory.Options createBitmapOptions(Request data) {
    final boolean justBounds = data.hasSize();
    final boolean hasConfig = data.config != null;
    BitmapFactory.Options options = null;
    if (justBounds || hasConfig || data.purgeable) {
      options = new BitmapFactory.Options();
      options.inJustDecodeBounds = justBounds;
      options.inInputShareable = data.purgeable;
      options.inPurgeable = data.purgeable;
      if (hasConfig) {
        options.inPreferredConfig = data.config;
      }
    }
    return options;
  }

  static boolean requiresInSampleSize(BitmapFactory.Options options) {
    return options != null && options.inJustDecodeBounds;
  }

  static void calculateInSampleSize(int reqWidth, int reqHeight, BitmapFactory.Options options,
      Request request) {
    calculateInSampleSize(reqWidth, reqHeight, options.outWidth, options.outHeight, options,
        request);
  }

  static void calculateInSampleSize(int reqWidth, int reqHeight, int width, int height,
      BitmapFactory.Options options, Request request) {
    int sampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
      final int heightRatio;
      final int widthRatio;
      if (reqHeight == 0) {
        sampleSize = (int) Math.floor((float) width / (float) reqWidth);
      } else if (reqWidth == 0) {
        sampleSize = (int) Math.floor((float) height / (float) reqHeight);
      } else {
        heightRatio = (int) Math.floor((float) height / (float) reqHeight);
        widthRatio = (int) Math.floor((float) width / (float) reqWidth);
        sampleSize = request.centerInside
            ? Math.max(heightRatio, widthRatio)
            : Math.min(heightRatio, widthRatio);
      }
    }
    options.inSampleSize = sampleSize;
    options.inJustDecodeBounds = false;
  }

  /**
   * Decode a byte stream into a Bitmap. This method will take into account additional information
   * about the supplied request in order to do the decoding efficiently (such as through leveraging
   * {@code inSampleSize}).
   */
  static Bitmap decodeStream(Source source, Request request) throws IOException {
    BufferedSource bufferedSource = Okio.buffer(source);

    boolean isWebPFile = Utils.isWebPFile(bufferedSource);
    boolean isPurgeable = request.purgeable && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    BitmapFactory.Options options = RequestHandler.createBitmapOptions(request);
    boolean calculateSize = RequestHandler.requiresInSampleSize(options);

    Bitmap bitmap;
    // We decode from a byte array because, a) when decoding a WebP network stream, BitmapFactory
    // throws a JNI Exception, so we workaround by decoding a byte array, or b) user requested
    // purgeable, which only affects bitmaps decoded from byte arrays.
    if (isWebPFile || isPurgeable) {
      byte[] bytes = bufferedSource.readByteArray();
      if (calculateSize) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        RequestHandler.calculateInSampleSize(request.targetWidth, request.targetHeight, options,
            request);
      }
      bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    } else {
      if (calculateSize) {
        InputStream stream = new SourceBufferingInputStream(bufferedSource);
        BitmapFactory.decodeStream(stream, null, options);
        RequestHandler.calculateInSampleSize(request.targetWidth, request.targetHeight, options,
            request);
      }
      bitmap = BitmapFactory.decodeStream(bufferedSource.inputStream(), null, options);
    }
    if (bitmap == null) {
      // Treat null as an IO exception, we will eventually retry.
      throw new IOException("Failed to decode bitmap.");
    }
    return bitmap;
  }

  static boolean isXmlResource(Resources resources, @DrawableRes int drawableId) {
    TypedValue typedValue = new TypedValue();
    resources.getValue(drawableId, typedValue, true);
    CharSequence file = typedValue.string;
    return file != null && file.toString().endsWith(".xml");
  }
}
