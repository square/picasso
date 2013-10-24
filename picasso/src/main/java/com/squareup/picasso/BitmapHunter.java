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
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.NetworkInfo;
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
  private static final String ANDROID_ASSET = "android_asset";
  protected static final int ASSET_PREFIX_LENGTH =
      (SCHEME_FILE + ":///" + ANDROID_ASSET + "/").length();

  final Picasso picasso;
  final Dispatcher dispatcher;
  final Cache cache;
  final Stats stats;
  final String key;
  final Request data;
  final List<Action> actions;
  final boolean skipMemoryCache;

  Bitmap result;
  Future<?> future;
  Picasso.LoadedFrom loadedFrom;
  Exception exception;
  int exifRotation; // Determined during decoding of original resource.

  BitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats, Action action) {
    this.picasso = picasso;
    this.dispatcher = dispatcher;
    this.cache = cache;
    this.stats = stats;
    this.key = action.getKey();
    this.data = action.getData();
    this.skipMemoryCache = action.skipCache;
    this.actions = new ArrayList<Action>(4);
    attach(action);
  }

  protected void setExifRotation(int exifRotation) {
    this.exifRotation = exifRotation;
  }

  @Override public void run() {
    try {
      Thread.currentThread().setName(Utils.THREAD_PREFIX + data.getName());

      result = hunt();

      if (result == null) {
        dispatcher.dispatchFailed(this);
      } else {
        dispatcher.dispatchComplete(this);
      }
    } catch (IOException e) {
      exception = e;
      dispatcher.dispatchRetry(this);
    } catch (Exception e) {
      exception = e;
      dispatcher.dispatchFailed(this);
    } finally {
      Thread.currentThread().setName(Utils.THREAD_IDLE_NAME);
    }
  }

  abstract Bitmap decode(Request data) throws IOException;

  Bitmap hunt() throws IOException {
    Bitmap bitmap;

    if (!skipMemoryCache) {
      bitmap = cache.get(key);
      if (bitmap != null) {
        stats.dispatchCacheHit();
        loadedFrom = MEMORY;
        return bitmap;
      }
    }

    bitmap = decode(data);

    if (bitmap != null) {
      stats.dispatchBitmapDecoded(bitmap);
      if (data.needsTransformation() || exifRotation != 0) {
        synchronized (DECODE_LOCK) {
          if (data.needsMatrixTransform() || exifRotation != 0) {
            bitmap = transformResult(data, bitmap, exifRotation);
          }
          if (data.hasCustomTransformations()) {
            bitmap = applyCustomTransformations(data.transformations, bitmap);
          }
        }
        stats.dispatchBitmapTransformed(bitmap);
      }
    }

    return bitmap;
  }

  void attach(Action action) {
    actions.add(action);
  }

  void detach(Action action) {
    actions.remove(action);
  }

  boolean cancel() {
    return actions.isEmpty() && future != null && future.cancel(false);
  }

  boolean isCancelled() {
    return future != null && future.isCancelled();
  }

  boolean shouldSkipMemoryCache() {
    return skipMemoryCache;
  }

  boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
    return false;
  }

  Bitmap getResult() {
    return result;
  }

  String getKey() {
    return key;
  }

  Request getData() {
    return data;
  }

  List<Action> getActions() {
    return actions;
  }

  Exception getException() {
    return exception;
  }

  Picasso.LoadedFrom getLoadedFrom() {
    return loadedFrom;
  }

  static BitmapHunter forRequest(Context context, Picasso picasso, Dispatcher dispatcher,
      Cache cache, Stats stats, Action action, Downloader downloader) {
    if (action.getData().resourceId != 0) {
      return new ResourceBitmapHunter(context, picasso, dispatcher, cache, stats, action);
    }
    Uri uri = action.getData().uri;
    String scheme = uri.getScheme();
    if (SCHEME_CONTENT.equals(scheme)) {
      if (Contacts.CONTENT_URI.getHost().equals(uri.getHost()) //
          && !uri.getPathSegments().contains(Contacts.Photo.CONTENT_DIRECTORY)) {
        return new ContactsPhotoBitmapHunter(context, picasso, dispatcher, cache, stats, action);
      } else {
        return new ContentProviderBitmapHunter(context, picasso, dispatcher, cache, stats, action);
      }
    } else if (SCHEME_FILE.equals(scheme)) {
      if (!uri.getPathSegments().isEmpty() && ANDROID_ASSET.equals(uri.getPathSegments().get(0))) {
        return new AssetBitmapHunter(context, picasso, dispatcher, cache, stats, action);
      }
      return new FileBitmapHunter(context, picasso, dispatcher, cache, stats, action);
    } else if (SCHEME_ANDROID_RESOURCE.equals(scheme)) {
      return new ResourceBitmapHunter(context, picasso, dispatcher, cache, stats, action);
    } else {
      return new NetworkBitmapHunter(picasso, dispatcher, cache, stats, action, downloader);
    }
  }

  static void calculateInSampleSize(int reqWidth, int reqHeight, BitmapFactory.Options options) {
    final int height = options.outHeight;
    final int width = options.outWidth;
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

  static Bitmap transformResult(Request data, Bitmap result, int exifRotation) {
    int inWidth = result.getWidth();
    int inHeight = result.getHeight();

    int drawX = 0;
    int drawY = 0;
    int drawWidth = inWidth;
    int drawHeight = inHeight;

    Matrix matrix = new Matrix();

    if (data.needsMatrixTransform()) {
      int targetWidth = data.targetWidth;
      int targetHeight = data.targetHeight;

      float targetRotation = data.rotationDegrees;
      if (targetRotation != 0) {
        if (data.hasRotationPivot) {
          matrix.setRotate(targetRotation, data.rotationPivotX, data.rotationPivotY);
        } else {
          matrix.setRotate(targetRotation);
        }
      }

      if (data.centerCrop) {
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
      } else if (data.centerInside) {
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
    }

    if (exifRotation != 0) {
      matrix.preRotate(exifRotation);
    }

    Bitmap newResult =
        Bitmap.createBitmap(result, drawX, drawY, drawWidth, drawHeight, matrix, true);
    if (newResult != result) {
      result.recycle();
      result = newResult;
    }

    return result;
  }
}
