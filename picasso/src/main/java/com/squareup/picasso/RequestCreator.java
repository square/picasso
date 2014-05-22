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

import android.app.Notification;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.TestOnly;

import static com.squareup.picasso.BitmapHunter.forRequest;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.PicassoDrawable.setBitmap;
import static com.squareup.picasso.PicassoDrawable.setPlaceholder;
import static com.squareup.picasso.RemoteViewsAction.AppWidgetAction;
import static com.squareup.picasso.RemoteViewsAction.NotificationAction;
import static com.squareup.picasso.Utils.OWNER_MAIN;
import static com.squareup.picasso.Utils.VERB_CHANGED;
import static com.squareup.picasso.Utils.VERB_COMPLETED;
import static com.squareup.picasso.Utils.VERB_CREATED;
import static com.squareup.picasso.Utils.checkMain;
import static com.squareup.picasso.Utils.checkNotMain;
import static com.squareup.picasso.Utils.createKey;
import static com.squareup.picasso.Utils.isMain;
import static com.squareup.picasso.Utils.log;

/** Fluent API for building an image download request. */
@SuppressWarnings("UnusedDeclaration") // Public API.
public class RequestCreator {
  private static int nextId = 0;

  private static int getRequestId() {
    if (isMain()) {
      return nextId++;
    }

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicInteger id = new AtomicInteger();
    Picasso.HANDLER.post(new Runnable() {
      @Override public void run() {
        id.set(getRequestId());
        latch.countDown();
      }
    });
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Picasso.HANDLER.post(new Runnable() {
        @Override public void run() {
          throw new RuntimeException(e);
        }
      });
    }
    return id.get();
  }

  private final Picasso picasso;
  private final Request.Builder data;

  private boolean skipMemoryCache;
  private boolean noFade;
  private boolean deferred;
  private int placeholderResId;
  private int errorResId;
  private Drawable placeholderDrawable;
  private Drawable errorDrawable;

  RequestCreator(Picasso picasso, Uri uri, int resourceId) {
    if (picasso.shutdown) {
      throw new IllegalStateException(
          "Picasso instance already shut down. Cannot submit new requests.");
    }
    this.picasso = picasso;
    this.data = new Request.Builder(uri, resourceId);
  }

  @TestOnly RequestCreator() {
    this.picasso = null;
    this.data = new Request.Builder(null, 0);
  }

  /**
   * A placeholder drawable to be used while the image is being loaded. If the requested image is
   * not immediately available in the memory cache then this resource will be set on the target
   * {@link ImageView}.
   */
  public RequestCreator placeholder(int placeholderResId) {
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
   * <p>
   * If you are not using a placeholder image but want to clear an existing image (such as when
   * used in an {@link android.widget.Adapter adapter}), pass in {@code null}.
   */
  public RequestCreator placeholder(Drawable placeholderDrawable) {
    if (placeholderResId != 0) {
      throw new IllegalStateException("Placeholder image already set.");
    }
    this.placeholderDrawable = placeholderDrawable;
    return this;
  }

  /** An error drawable to be used if the request image could not be loaded. */
  public RequestCreator error(int errorResId) {
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
  public RequestCreator error(Drawable errorDrawable) {
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
   * will result in delayed execution of the request until the {@link ImageView} has been measured.
   * <p>
   * <em>Note:</em> This method works only when your target is an {@link ImageView}.
   */
  public RequestCreator fit() {
    deferred = true;
    return this;
  }

  /** Internal use only. Used by {@link DeferredRequestCreator}. */
  RequestCreator unfit() {
    deferred = false;
    return this;
  }

  /** Resize the image to the specified dimension size. */
  public RequestCreator resizeDimen(int targetWidthResId, int targetHeightResId) {
    Resources resources = picasso.context.getResources();
    int targetWidth = resources.getDimensionPixelSize(targetWidthResId);
    int targetHeight = resources.getDimensionPixelSize(targetHeightResId);
    return resize(targetWidth, targetHeight);
  }

  /** Resize the image to the specified size in pixels. */
  public RequestCreator resize(int targetWidth, int targetHeight) {
    data.resize(targetWidth, targetHeight);
    return this;
  }

  /**
   * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
   * distorting the aspect ratio. This cropping technique scales the image so that it fills the
   * requested bounds and then crops the extra.
   */
  public RequestCreator centerCrop() {
    data.centerCrop();
    return this;
  }

  /**
   * Centers an image inside of the bounds specified by {@link #resize(int, int)}. This scales
   * the image so that both dimensions are equal to or less than the requested bounds.
   */
  public RequestCreator centerInside() {
    data.centerInside();
    return this;
  }

  /** Rotate the image by the specified degrees. */
  public RequestCreator rotate(float degrees) {
    data.rotate(degrees);
    return this;
  }

  /** Rotate the image by the specified degrees around a pivot point. */
  public RequestCreator rotate(float degrees, float pivotX, float pivotY) {
    data.rotate(degrees, pivotX, pivotY);
    return this;
  }

  /**
   * Attempt to decode the image using the specified config.
   * <p>
   * Note: This value may be ignored by {@link BitmapFactory}. See
   * {@link BitmapFactory.Options#inPreferredConfig its documentation} for more details.
   */
  public RequestCreator config(Bitmap.Config config) {
    data.config(config);
    return this;
  }

  /**
   * Add a custom transformation to be applied to the image.
   * <p>
   * Custom transformations will always be run after the built-in transformations.
   */
  // TODO show example of calling resize after a transform in the javadoc
  public RequestCreator transform(Transformation transformation) {
    data.transform(transformation);
    return this;
  }

  /**
   * Indicate that this action should not use the memory cache for attempting to load or save the
   * image. This can be useful when you know an image will only ever be used once (e.g., loading
   * an image from the filesystem and uploading to a remote server).
   */
  public RequestCreator skipMemoryCache() {
    skipMemoryCache = true;
    return this;
  }

  /** Disable brief fade in of images loaded from the disk cache or network. */
  public RequestCreator noFade() {
    noFade = true;
    return this;
  }

  /**
   * Synchronously fulfill this request. Must not be called from the main thread.
   * <p>
   * <em>Note</em>: The result of this operation is not cached in memory because the underlying
   * {@link Cache} implementation is not guaranteed to be thread-safe.
   */
  public Bitmap get() throws IOException {
    long started = System.nanoTime();
    checkNotMain();

    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with get.");
    }
    if (!data.hasImage()) {
      return null;
    }

    Request finalData = createRequest(started);
    String key = createKey(finalData, new StringBuilder());

    Action action = new GetAction(picasso, finalData, skipMemoryCache, key);
    return forRequest(picasso.context, picasso, picasso.dispatcher, picasso.cache, picasso.stats,
        action, picasso.dispatcher.downloader).hunt();
  }

  /**
   * Asynchronously fulfills the request without a {@link ImageView} or {@link Target}. This is
   * useful when you want to warm up the cache with an image.
   * <p>
   * <em>Note:</em> It is safe to invoke this method from any thread.
   */
  public void fetch() {
    long started = System.nanoTime();

    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with fetch.");
    }
    if (data.hasImage()) {
      Request request = createRequest(started);
      String key = createKey(request, new StringBuilder());

      Action action = new FetchAction(picasso, request, skipMemoryCache, key);
      picasso.submit(action);
    }
  }

  /**
   * Asynchronously fulfills the request into the specified {@link Target}. In most cases, you
   * should use this when you are dealing with a custom {@link android.view.View View} or view
   * holder which should implement the {@link Target} interface.
   * <p>
   * Implementing on a {@link android.view.View View}:
   * <blockquote><pre>
   * public class ProfileView extends FrameLayout implements Target {
   *   {@literal @}Override public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
   *     setBackgroundDrawable(new BitmapDrawable(bitmap));
   *   }
   *
   *   {@literal @}Override public void onBitmapFailed() {
   *     setBackgroundResource(R.drawable.profile_error);
   *   }
   *
   *   {@literal @}Override public void onPrepareLoad(Drawable placeHolderDrawable) {
   *     frame.setBackgroundDrawable(placeHolderDrawable);
   *   }
   * }
   * </pre></blockquote>
   * Implementing on a view holder object for use inside of an adapter:
   * <blockquote><pre>
   * public class ViewHolder implements Target {
   *   public FrameLayout frame;
   *   public TextView name;
   *
   *   {@literal @}Override public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
   *     frame.setBackgroundDrawable(new BitmapDrawable(bitmap));
   *   }
   *
   *   {@literal @}Override public void onBitmapFailed() {
   *     frame.setBackgroundResource(R.drawable.profile_error);
   *   }
   *
   *   {@literal @}Override public void onPrepareLoad(Drawable placeHolderDrawable) {
   *     frame.setBackgroundDrawable(placeHolderDrawable);
   *   }
   * }
   * </pre></blockquote>
   * <p>
   * <em>Note:</em> This method keeps a weak reference to the {@link Target} instance and will be
   * garbage collected if you do not keep a strong reference to it. To receive callbacks when an
   * image is loaded use {@link #into(android.widget.ImageView, Callback)}.
   */
  public void into(Target target) {
    long started = System.nanoTime();
    checkMain();

    if (target == null) {
      throw new IllegalArgumentException("Target must not be null.");
    }
    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with a Target.");
    }

    Drawable drawable =
        placeholderResId != 0 ? picasso.context.getResources().getDrawable(placeholderResId)
            : placeholderDrawable;

    if (!data.hasImage()) {
      picasso.cancelRequest(target);
      target.onPrepareLoad(drawable);
      return;
    }

    Request request = createRequest(started);
    String requestKey = createKey(request);

    if (!skipMemoryCache) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(requestKey);
      if (bitmap != null) {
        picasso.cancelRequest(target);
        target.onBitmapLoaded(bitmap, MEMORY);
        return;
      }
    }

    target.onPrepareLoad(drawable);

    Action action =
        new TargetAction(picasso, target, request, skipMemoryCache, errorResId, errorDrawable,
            requestKey);
    picasso.enqueueAndSubmit(action);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into a {@link Notification}.
   */
  public void into(RemoteViews remoteViews, int viewId, int notificationId,
      Notification notification) {
    long started = System.nanoTime();
    checkMain();

    if (remoteViews == null) {
      throw new IllegalArgumentException("RemoteViews must not be null.");
    }
    if (notification == null) {
      throw new IllegalArgumentException("Notification must not be null.");
    }
    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with RemoteViews.");
    }
    if (placeholderDrawable != null || errorDrawable != null) {
      throw new IllegalArgumentException(
          "Cannot use placeholder or error drawables with remote views.");
    }

    Request request = createRequest(started);
    String key = createKey(request);

    RemoteViewsAction action =
        new NotificationAction(picasso, request, remoteViews, viewId, notificationId, notification,
            skipMemoryCache, errorResId, key);

    performRemoteViewInto(action);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into all instances of a widget.
   */
  public void into(RemoteViews remoteViews, int viewId, int[] appWidgetIds) {
    long started = System.nanoTime();
    checkMain();

    if (remoteViews == null) {
      throw new IllegalArgumentException("remoteViews must not be null.");
    }
    if (appWidgetIds == null) {
      throw new IllegalArgumentException("appWidgetIds must not be null.");
    }
    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with remote views.");
    }
    if (placeholderDrawable != null || errorDrawable != null) {
      throw new IllegalArgumentException(
          "Cannot use placeholder or error drawables with remote views.");
    }

    Request request = createRequest(started);
    String key = createKey(request);

    RemoteViewsAction action =
        new AppWidgetAction(picasso, request, remoteViews, viewId, appWidgetIds, skipMemoryCache,
            errorResId, key);

    performRemoteViewInto(action);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link ImageView}.
   * <p>
   * <em>Note:</em> This method keeps a weak reference to the {@link ImageView} instance and will
   * automatically support object recycling.
   */
  public void into(ImageView target) {
    into(target, null);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link ImageView} and invokes the
   * target {@link Callback} if it's not {@code null}.
   * <p>
   * <em>Note:</em> The {@link Callback} param is a strong reference and will prevent your
   * {@link android.app.Activity} or {@link android.app.Fragment} from being garbage collected. If
   * you use this method, it is <b>strongly</b> recommended you invoke an adjacent
   * {@link Picasso#cancelRequest(android.widget.ImageView)} call to prevent temporary leaking.
   */
  public void into(ImageView target, Callback callback) {
    long started = System.nanoTime();
    checkMain();

    if (target == null) {
      throw new IllegalArgumentException("Target must not be null.");
    }

    if (!data.hasImage()) {
      picasso.cancelRequest(target);
      setPlaceholder(target, placeholderResId, placeholderDrawable);
      return;
    }

    if (deferred) {
      if (data.hasSize()) {
        throw new IllegalStateException("Fit cannot be used with resize.");
      }
      int measuredWidth = target.getMeasuredWidth();
      int measuredHeight = target.getMeasuredHeight();
      if (measuredWidth == 0 || measuredHeight == 0) {
        setPlaceholder(target, placeholderResId, placeholderDrawable);
        picasso.defer(target, new DeferredRequestCreator(this, target, callback));
        return;
      }
      data.resize(measuredWidth, measuredHeight);
    }

    Request request = createRequest(started);
    String requestKey = createKey(request);

    if (!skipMemoryCache) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(requestKey);
      if (bitmap != null) {
        picasso.cancelRequest(target);
        setBitmap(target, picasso.context, bitmap, MEMORY, noFade, picasso.indicatorsEnabled);
        if (picasso.loggingEnabled) {
          log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + MEMORY);
        }
        if (callback != null) {
          callback.onSuccess();
        }
        return;
      }
    }

    setPlaceholder(target, placeholderResId, placeholderDrawable);

    Action action =
        new ImageViewAction(picasso, target, request, skipMemoryCache, noFade, errorResId,
            errorDrawable, requestKey, callback);

    picasso.enqueueAndSubmit(action);
  }

  /** Create the request optionally passing it through the request transformer. */
  private Request createRequest(long started) {
    int id = getRequestId();

    Request request = data.build();
    request.id = id;
    request.started = started;

    boolean loggingEnabled = picasso.loggingEnabled;
    if (loggingEnabled) {
      log(OWNER_MAIN, VERB_CREATED, request.plainId(), request.toString());
    }

    Request transformed = picasso.transformRequest(request);
    if (transformed != request) {
      // If the request was changed, copy over the id and timestamp from the original.
      transformed.id = id;
      transformed.started = started;

      if (loggingEnabled) {
        log(OWNER_MAIN, VERB_CHANGED, transformed.logId(), "into " + transformed);
      }
    }

    return transformed;
  }

  private void performRemoteViewInto(RemoteViewsAction action) {
    if (!skipMemoryCache) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(action.getKey());
      if (bitmap != null) {
        action.complete(bitmap, MEMORY);
        return;
      }
    }

    if (placeholderResId != 0) {
      action.setImageResource(placeholderResId);
    }

    picasso.enqueueAndSubmit(action);
  }
}
