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
import android.graphics.Point;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.content.ContentResolver.SCHEME_FILE;
import static android.provider.ContactsContract.Contacts;
import static com.squareup.picasso.AssetBitmapHunter.ANDROID_ASSET;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.Utils.OWNER_HUNTER;
import static com.squareup.picasso.Utils.VERB_DECODED;
import static com.squareup.picasso.Utils.VERB_EXECUTING;
import static com.squareup.picasso.Utils.VERB_JOINED;
import static com.squareup.picasso.Utils.VERB_REMOVED;
import static com.squareup.picasso.Utils.VERB_TRANSFORMED;
import static com.squareup.picasso.Utils.getLogIdsForHunter;
import static com.squareup.picasso.Utils.log;

abstract class BitmapHunter implements Runnable {

  /**
   * Global lock for bitmap decoding to ensure that we are only are decoding one at a time. Since
   * this will only ever happen in background threads we help avoid excessive memory thrashing as
   * well as potential OOMs. Shamelessly stolen from Volley.
   */
  private static final Object DECODE_LOCK = new Object();

  private static final ThreadLocal<StringBuilder> NAME_BUILDER = new ThreadLocal<StringBuilder>() {
    @Override protected StringBuilder initialValue() {
      return new StringBuilder(Utils.THREAD_PREFIX);
    }
  };

  final Picasso picasso;
  final Dispatcher dispatcher;
  final Cache cache;
  final Stats stats;
  final String key;
  final Request data;
  final boolean skipMemoryCache;

  Action action;
  List<Action> actions;
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
    this.data = action.getRequest();
    this.skipMemoryCache = action.skipCache;
    this.action = action;
  }

  protected void setExifRotation(int exifRotation) {
    this.exifRotation = exifRotation;
  }

  @Override public void run() {
    try {
      updateThreadName(data);

      if (picasso.loggingEnabled) {
        log(OWNER_HUNTER, VERB_EXECUTING, getLogIdsForHunter(this));
      }

      result = hunt();

      if (result == null) {
        dispatcher.dispatchFailed(this);
      } else {
        dispatcher.dispatchComplete(this);
      }
    } catch (Downloader.ResponseException e) {
      exception = e;
      dispatcher.dispatchFailed(this);
    } catch (IOException e) {
      exception = e;
      dispatcher.dispatchRetry(this);
    } catch (OutOfMemoryError e) {
      StringWriter writer = new StringWriter();
      stats.createSnapshot().dump(new PrintWriter(writer));
      exception = new RuntimeException(writer.toString(), e);
      dispatcher.dispatchFailed(this);
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
        if (picasso.loggingEnabled) {
          log(OWNER_HUNTER, VERB_DECODED, data.logId(), "from cache");
        }
        return bitmap;
      }
    }

    bitmap = decode(data);

    if (bitmap != null) {
      if (picasso.loggingEnabled) {
        log(OWNER_HUNTER, VERB_DECODED, data.logId());
      }
      stats.dispatchBitmapDecoded(bitmap);
      if (data.needsTransformation() || exifRotation != 0) {
        synchronized (DECODE_LOCK) {
          if (data.needsMatrixTransform() || exifRotation != 0) {
            bitmap = transformResult(data, bitmap, exifRotation);
            if (picasso.loggingEnabled) {
              log(OWNER_HUNTER, VERB_TRANSFORMED, data.logId());
            }
          }
          if (data.hasCustomTransformations()) {
            bitmap = applyCustomTransformations(data.transformations, bitmap);
            if (picasso.loggingEnabled) {
              log(OWNER_HUNTER, VERB_TRANSFORMED, data.logId(), "from custom transformations");
            }
          }
        }
        if (bitmap != null) {
          stats.dispatchBitmapTransformed(bitmap);
        }
      }
    }

    return bitmap;
  }

  void attach(Action action) {
    boolean loggingEnabled = picasso.loggingEnabled;
    Request request = action.request;

    if (this.action == null) {
      this.action = action;
      if (loggingEnabled) {
        if (actions == null || actions.isEmpty()) {
          log(OWNER_HUNTER, VERB_JOINED, request.logId(), "to empty hunter");
        } else {
          log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
        }
      }
      return;
    }

    if (actions == null) {
      actions = new ArrayList<Action>(3);
    }

    actions.add(action);

    if (loggingEnabled) {
      log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
    }
  }

  void detach(Action action) {
    if (this.action == action) {
      this.action = null;
    } else if (actions != null) {
      actions.remove(action);
    }

    if (picasso.loggingEnabled) {
      log(OWNER_HUNTER, VERB_REMOVED, action.request.logId(), getLogIdsForHunter(this, "from "));
    }
  }

  boolean cancel() {
    return action == null
        && (actions == null || actions.isEmpty())
        && future != null
        && future.cancel(false);
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

  boolean supportsReplay() {
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

  Action getAction() {
    return action;
  }

  Picasso getPicasso() {
    return picasso;
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

  static void updateThreadName(Request data) {
    String name = data.getName();

    StringBuilder builder = NAME_BUILDER.get();
    builder.ensureCapacity(Utils.THREAD_PREFIX.length() + name.length());
    builder.replace(Utils.THREAD_PREFIX.length(), builder.length(), name);

    Thread.currentThread().setName(builder.toString());
  }

  static BitmapHunter forRequest(Context context, Picasso picasso, Dispatcher dispatcher,
      Cache cache, Stats stats, Action action, Downloader downloader) {
    if (action.getRequest().resourceId != 0) {
      return new ResourceBitmapHunter(context, picasso, dispatcher, cache, stats, action);
    }
    Uri uri = action.getRequest().uri;
    String scheme = uri.getScheme();
    if (SCHEME_CONTENT.equals(scheme)) {
      if (Contacts.CONTENT_URI.getHost().equals(uri.getHost()) //
          && !uri.getPathSegments().contains(Contacts.Photo.CONTENT_DIRECTORY)) {
        return new ContactsPhotoBitmapHunter(context, picasso, dispatcher, cache, stats, action);
      } else if (MediaStore.AUTHORITY.equals(uri.getAuthority())) {
        return new MediaStoreBitmapHunter(context, picasso, dispatcher, cache, stats, action);
      } else {
        return new ContentStreamBitmapHunter(context, picasso, dispatcher, cache, stats, action);
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

  /**
   * Lazily create {@link android.graphics.BitmapFactory.Options} based in given
   * {@link com.squareup.picasso.Request}, only instantiating them if needed.
   */
  static BitmapFactory.Options createBitmapOptions(Request data) {
    final boolean justBounds = data.hasSize();
    final boolean hasConfig = data.config != null;
    final boolean hasResizer = data.resizer != null;
    BitmapFactory.Options options = null;
    if (justBounds || hasConfig || hasResizer) {
      options = new BitmapFactory.Options();
      options.inJustDecodeBounds = justBounds || hasResizer;
      if (hasConfig) {
        options.inPreferredConfig = data.config;
      }
    }
    return options;
  }

  static boolean requiresInSampleSize(BitmapFactory.Options options) {
    return options != null && options.inJustDecodeBounds;
  }

  static void applyResizer(Request data, BitmapFactory.Options options) {
    if (data.resizer == null) return;
    Point pt = data.resizer.resize(options.outWidth, options.outHeight);
    if (pt != null) {
      data.targetWidth = pt.x;
      data.targetHeight = pt.y;
    }
  }

  static void calculateInSampleSize(int reqWidth, int reqHeight, BitmapFactory.Options options) {
    calculateInSampleSize(reqWidth, reqHeight, options.outWidth, options.outHeight, options);
  }

  static void calculateInSampleSize(int reqWidth, int reqHeight, int width, int height,
      BitmapFactory.Options options) {
    int sampleSize = 1;
    if (reqHeight > 0 && reqWidth > 0 && (height > reqHeight || width > reqWidth)) {
      final int heightRatio = (int) Math.floor((float) height / (float) reqHeight);
      final int widthRatio = (int) Math.floor((float) width / (float) reqWidth);
      sampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
    }
    options.inSampleSize = sampleSize;
    options.inJustDecodeBounds = false;
  }

  static Bitmap applyCustomTransformations(List<Transformation> transformations, Bitmap result) {
    for (int i = 0, count = transformations.size(); i < count; i++) {
      final Transformation transformation = transformations.get(i);
      Bitmap newResult = transformation.transform(result);

      if (newResult == null) {
        final StringBuilder builder = new StringBuilder() //
            .append("Transformation ")
            .append(transformation.key())
            .append(" returned null after ")
            .append(i)
            .append(" previous transformation(s).\n\nTransformation list:\n");
        for (Transformation t : transformations) {
          builder.append(t.key()).append('\n');
        }
        Picasso.HANDLER.post(new Runnable() {
          @Override public void run() {
            throw new NullPointerException(builder.toString());
          }
        });
        return null;
      }

      if (newResult == result && result.isRecycled()) {
        Picasso.HANDLER.post(new Runnable() {
          @Override public void run() {
            throw new IllegalStateException("Transformation "
                + transformation.key()
                + " returned input Bitmap but recycled it.");
          }
        });
        return null;
      }

      // If the transformation returned a new bitmap ensure they recycled the original.
      if (newResult != result && !result.isRecycled()) {
        Picasso.HANDLER.post(new Runnable() {
          @Override public void run() {
            throw new IllegalStateException("Transformation "
                + transformation.key()
                + " mutated input Bitmap but failed to recycle the original.");
          }
        });
        return null;
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
