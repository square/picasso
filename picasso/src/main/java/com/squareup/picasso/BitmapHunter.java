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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.content.ContentResolver.SCHEME_FILE;
import static android.provider.ContactsContract.Contacts;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;

abstract class BitmapHunter implements Runnable {

  /**
   * Global lock for bitmap decoding to ensure that we are only are decoding one at a time. Since
   * this will only ever happen in background threads we help avoid excessive memory thrashing as
   * well as potential OOMs. Shamelessly stolen from Volley.
   */
  private static final Object DECODE_LOCK = new Object();

  static final int DEFAULT_RETRY_COUNT = 2;

  final Picasso picasso;
  final Dispatcher dispatcher;
  final Cache cache;
  final String key;
  final Uri uri;
  final List<Transformation> transformations;
  final List<Request> requests;
  final PicassoBitmapOptions options;
  final boolean skipCache;

  Bitmap result;
  Future<?> future;
  Picasso.LoadedFrom loadedFrom;
  Exception exception;

  int retryCount = DEFAULT_RETRY_COUNT;

  BitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Request request) {
    this.picasso = picasso;
    this.dispatcher = dispatcher;
    this.cache = cache;
    this.key = request.getKey();
    this.uri = request.getUri();
    this.transformations = request.transformations;
    this.options = request.options;
    this.skipCache = request.skipCache;
    this.requests = new ArrayList<Request>(4);
    attach(request);
  }

  @Override public void run() {
    try {
      Thread.currentThread().setName(Utils.THREAD_PREFIX + getName());

      result = hunt();

      if (result == null) {
        dispatcher.dispatchFailed(this);
      } else {
        dispatcher.dispatchComplete(this);
      }
    } catch (IOException e) {
      exception = e;
      dispatcher.dispatchRetry(this);
    } finally {
      Thread.currentThread().setName(Utils.THREAD_IDLE_NAME);
    }
  }

  abstract Bitmap decode(Uri uri, PicassoBitmapOptions options, int retryCount) throws IOException;

  abstract Picasso.LoadedFrom getLoadedFrom();

  Bitmap hunt() throws IOException {
    Bitmap bitmap;

    if (!skipCache) {
      bitmap = cache.get(key);
      if (bitmap != null) {
        loadedFrom = MEMORY;
        return bitmap;
      }
    }

    bitmap = decode(uri, options, retryCount);

    if (bitmap != null && (options != null || transformations != null)) {
      synchronized (DECODE_LOCK) {
        if (options != null) {
          bitmap = transformResult(options, bitmap, options.exifRotation);
        }
        if (transformations != null) {
          bitmap = applyCustomTransformations(transformations, bitmap);
        }
      }
    }

    return bitmap;
  }

  void attach(Request request) {
    requests.add(request);
  }

  void detach(Request request) {
    requests.remove(request);
  }

  boolean cancel() {
    return requests.isEmpty() && future != null && future.cancel(false);
  }

  boolean isCancelled() {
    return future.isCancelled();
  }

  boolean shouldSkipCache() {
    return skipCache;
  }

  Bitmap getResult() {
    return result;
  }

  String getKey() {
    return key;
  }

  String getName() {
    return uri.getPath();
  }

  static BitmapHunter forRequest(Context context, Picasso picasso, Dispatcher dispatcher,
      Cache cache, Request request, Downloader downloader) {
    if (request.getResourceId() != 0) {
      return new ResourceBitmapHunter(context, picasso, dispatcher, cache, request);
    }
    Uri uri = request.getUri();
    String scheme = uri.getScheme();
    if (SCHEME_CONTENT.equals(scheme)) {
      if (Contacts.CONTENT_URI.getHost().equals(uri.getHost()) //
          && !uri.getPathSegments().contains(Contacts.Photo.CONTENT_DIRECTORY)) {
        return new ContactsPhotoBitmapHunter(context, picasso, dispatcher, cache, request);
      } else {
        return new ContentProviderBitmapHunter(context, picasso, dispatcher, cache, request);
      }
    } else if (SCHEME_FILE.equals(scheme)) {
      return new FileBitmapHunter(context, picasso, dispatcher, cache, request);
    } else if (SCHEME_ANDROID_RESOURCE.equals(scheme)) {
      return new ResourceBitmapHunter(context, picasso, dispatcher, cache, request);
    } else {
      return new NetworkBitmapHunter(picasso, dispatcher, cache, request, downloader);
    }
  }

  static void calculateInSampleSize(PicassoBitmapOptions options) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    final int reqHeight = options.targetHeight;
    final int reqWidth = options.targetWidth;
    int sampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
      final int heightRatio = Math.round((float) height / (float) reqHeight);
      final int widthRatio = Math.round((float) width / (float) reqWidth);
      sampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
    }

    options.inSampleSize = sampleSize;
    options.inJustDecodeBounds = false;
  }

  static Bitmap applyCustomTransformations(List<Transformation> transformations, Bitmap result) {
    for (int i = 0, count = transformations.size(); i < count; i++) {
      Transformation transformation = transformations.get(i);
      Bitmap newResult = transformation.transform(result);

      if (newResult == null) {
        StringBuilder builder = new StringBuilder() //
            .append("Transformation ")
            .append(transformation.key())
            .append(" returned null after ")
            .append(i)
            .append(" previous transformation(s).\n\nTransformation list:\n");
        for (Transformation t : transformations) {
          builder.append(t.key()).append('\n');
        }
        throw new NullPointerException(builder.toString());
      }

      if (newResult == result && result.isRecycled()) {
        throw new IllegalStateException(
            "Transformation " + transformation.key() + " returned input Bitmap but recycled it.");
      }

      // If the transformation returned a new bitmap ensure they recycled the original.
      if (newResult != result && !result.isRecycled()) {
        throw new IllegalStateException("Transformation "
            + transformation.key()
            + " mutated input Bitmap but failed to recycle the original.");
      }
      result = newResult;
    }
    return result;
  }

  static Bitmap transformResult(PicassoBitmapOptions options, Bitmap result, int exifRotation) {
    int inWidth = result.getWidth();
    int inHeight = result.getHeight();

    int drawX = 0;
    int drawY = 0;
    int drawWidth = inWidth;
    int drawHeight = inHeight;

    Matrix matrix = new Matrix();

    if (options != null) {
      int targetWidth = options.targetWidth;
      int targetHeight = options.targetHeight;

      float targetRotation = options.targetRotation;
      if (targetRotation != 0) {
        if (options.hasRotationPivot) {
          matrix.setRotate(targetRotation, options.targetPivotX, options.targetPivotY);
        } else {
          matrix.setRotate(targetRotation);
        }
      }

      if (options.centerCrop) {
        float widthRatio = targetWidth / (float) inWidth;
        float heightRatio = targetHeight / (float) inHeight;
        float scale;
        if (widthRatio > heightRatio) {
          scale = widthRatio;
          int newSize = (int) Math.ceil(inHeight * (heightRatio / widthRatio));
          drawY = (inHeight - newSize) / 2;
          drawHeight = newSize;
        } else {
          scale = heightRatio;
          int newSize = (int) Math.ceil(inWidth * (widthRatio / heightRatio));
          drawX = (inWidth - newSize) / 2;
          drawWidth = newSize;
        }
        matrix.preScale(scale, scale);
      } else if (options.centerInside) {
        float widthRatio = targetWidth / (float) inWidth;
        float heightRatio = targetHeight / (float) inHeight;
        float scale = widthRatio < heightRatio ? widthRatio : heightRatio;
        matrix.preScale(scale, scale);
      } else if (targetWidth != 0 && targetHeight != 0 //
          && (targetWidth != inWidth || targetHeight != inHeight)) {
        // If an explicit target size has been specified and they do not match the results bounds,
        // pre-scale the existing matrix appropriately.
        float sx = targetWidth / (float) inWidth;
        float sy = targetHeight / (float) inHeight;
        matrix.preScale(sx, sy);
      }

      float targetScaleX = options.targetScaleX;
      float targetScaleY = options.targetScaleY;
      if (targetScaleX != 0 || targetScaleY != 0) {
        matrix.setScale(targetScaleX, targetScaleY);
      }
    }

    if (exifRotation != 0) {
      matrix.preRotate(exifRotation);
    }

    Bitmap newResult =
        Bitmap.createBitmap(result, drawX, drawY, drawWidth, drawHeight, matrix, false);
    if (newResult != result) {
      result.recycle();
      result = newResult;
    }

    return result;
  }
}
