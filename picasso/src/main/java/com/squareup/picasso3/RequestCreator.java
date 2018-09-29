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

import android.app.Notification;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.RemoteViews;
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.squareup.picasso3.BitmapHunter.forRequest;
import static com.squareup.picasso3.MemoryPolicy.shouldReadFromMemoryCache;
import static com.squareup.picasso3.MemoryPolicy.shouldWriteToMemoryCache;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso3.Picasso.Priority;
import static com.squareup.picasso3.PicassoDrawable.setPlaceholder;
import static com.squareup.picasso3.PicassoDrawable.setResult;
import static com.squareup.picasso3.RemoteViewsAction.AppWidgetAction;
import static com.squareup.picasso3.RemoteViewsAction.NotificationAction;
import static com.squareup.picasso3.Utils.OWNER_MAIN;
import static com.squareup.picasso3.Utils.VERB_CHANGED;
import static com.squareup.picasso3.Utils.VERB_COMPLETED;
import static com.squareup.picasso3.Utils.VERB_CREATED;
import static com.squareup.picasso3.Utils.checkMain;
import static com.squareup.picasso3.Utils.checkNotMain;
import static com.squareup.picasso3.Utils.log;

/** Fluent API for building an image download request. */
@SuppressWarnings("UnusedDeclaration") // Public API.
public class RequestCreator {
  private static final AtomicInteger nextId = new AtomicInteger();

  private final Picasso picasso;
  private final Request.Builder data;

  private boolean noFade;
  private boolean deferred;
  private boolean setPlaceholder = true;
  private int placeholderResId;
  @DrawableRes private int errorResId;
  private @Nullable Drawable placeholderDrawable;
  private @Nullable Drawable errorDrawable;

  RequestCreator(Picasso picasso, @Nullable Uri uri, int resourceId) {
    if (picasso.shutdown) {
      throw new IllegalStateException(
          "Picasso instance already shut down. Cannot submit new requests.");
    }
    this.picasso = picasso;
    this.data = new Request.Builder(uri, resourceId, picasso.defaultBitmapConfig);
  }

  @SuppressWarnings("NullAway")
  @VisibleForTesting RequestCreator() {
    this.picasso = null;
    this.data = new Request.Builder(null, 0, null);
  }

  /**
   * Explicitly opt-out to having a placeholder set when calling {@code into}.
   * <p>
   * By default, Picasso will either set a supplied placeholder or clear the target
   * {@link ImageView} in order to ensure behavior in situations where views are recycled. This
   * method will prevent that behavior and retain any already set image.
   */
  @NonNull
  public RequestCreator noPlaceholder() {
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
   * A placeholder drawable to be used while the image is being loaded. If the requested image is
   * not immediately available in the memory cache then this resource will be set on the target
   * {@link ImageView}.
   */
  @NonNull
  public RequestCreator placeholder(@DrawableRes int placeholderResId) {
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
   * not immediately available in the memory cache then this resource will be set on the target
   * {@link ImageView}.
   * <p>
   * If you are not using a placeholder image but want to clear an existing image (such as when
   * used in an {@link android.widget.Adapter adapter}), pass in {@code null}.
   */
  @NonNull
  public RequestCreator placeholder(@NonNull Drawable placeholderDrawable) {
    if (!setPlaceholder) {
      throw new IllegalStateException("Already explicitly declared as no placeholder.");
    }
    if (placeholderResId != 0) {
      throw new IllegalStateException("Placeholder image already set.");
    }
    this.placeholderDrawable = placeholderDrawable;
    return this;
  }

  /** An error drawable to be used if the request image could not be loaded. */
  @NonNull
  public RequestCreator error(@DrawableRes int errorResId) {
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
  @NonNull
  public RequestCreator error(@NonNull Drawable errorDrawable) {
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
   * Assign a tag to this request. Tags are an easy way to logically associate
   * related requests that can be managed together e.g. paused, resumed,
   * or canceled.
   * <p>
   * You can either use simple {@link String} tags or objects that naturally
   * define the scope of your requests within your app such as a
   * {@link android.content.Context}, an {@link android.app.Activity}, or a
   * {@link android.app.Fragment}.
   *
   * <strong>WARNING:</strong>: Picasso will keep a reference to the tag for
   * as long as this tag is paused and/or has active requests. Look out for
   * potential leaks.
   *
   * @see Picasso#cancelTag(Object)
   * @see Picasso#pauseTag(Object)
   * @see Picasso#resumeTag(Object)
   */
  @NonNull
  public RequestCreator tag(@NonNull Object tag) {
    data.tag(tag);
    return this;
  }

  /**
   * Attempt to resize the image to fit exactly into the target {@link ImageView}'s bounds. This
   * will result in delayed execution of the request until the {@link ImageView} has been laid out.
   * <p>
   * <em>Note:</em> This method works only when your target is an {@link ImageView}.
   */
  @NonNull
  public RequestCreator fit() {
    deferred = true;
    return this;
  }

  /** Internal use only. Used by {@link DeferredRequestCreator}. */
  RequestCreator unfit() {
    deferred = false;
    return this;
  }

  /** Internal use only. Used by {@link DeferredRequestCreator}. */
  RequestCreator clearTag() {
    data.clearTag();
    return this;
  }

  /** Internal use only. Used by {@link DeferredRequestCreator}. */
  @Nullable Object getTag() {
    return data.getTag();
  }

  /**
   * Resize the image to the specified dimension size.
   * Use 0 as desired dimension to resize keeping aspect ratio.
   */
  @NonNull
  public RequestCreator resizeDimen(int targetWidthResId, int targetHeightResId) {
    Resources resources = picasso.context.getResources();
    int targetWidth = resources.getDimensionPixelSize(targetWidthResId);
    int targetHeight = resources.getDimensionPixelSize(targetHeightResId);
    return resize(targetWidth, targetHeight);
  }

  /**
   * Resize the image to the specified size in pixels.
   * Use 0 as desired dimension to resize keeping aspect ratio.
   */
  @NonNull
  public RequestCreator resize(int targetWidth, int targetHeight) {
    data.resize(targetWidth, targetHeight);
    return this;
  }

  /**
   * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
   * distorting the aspect ratio. This cropping technique scales the image so that it fills the
   * requested bounds and then crops the extra.
   */
  @NonNull
  public RequestCreator centerCrop() {
    data.centerCrop(Gravity.CENTER);
    return this;
  }

  /**
   * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
   * distorting the aspect ratio. This cropping technique scales the image so that it fills the
   * requested bounds and then crops the extra, preferring the contents at {@code alignGravity}.
   */
  @NonNull
  public RequestCreator centerCrop(int alignGravity) {
    data.centerCrop(alignGravity);
    return this;
  }

  /**
   * Centers an image inside of the bounds specified by {@link #resize(int, int)}. This scales
   * the image so that both dimensions are equal to or less than the requested bounds.
   */
  @NonNull
  public RequestCreator centerInside() {
    data.centerInside();
    return this;
  }

  /**
   * Only resize an image if the original image size is bigger than the target size
   * specified by {@link #resize(int, int)}.
   */
  @NonNull
  public RequestCreator onlyScaleDown() {
    data.onlyScaleDown();
    return this;
  }

  /** Rotate the image by the specified degrees. */
  @NonNull
  public RequestCreator rotate(float degrees) {
    data.rotate(degrees);
    return this;
  }

  /** Rotate the image by the specified degrees around a pivot point. */
  @NonNull
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
  @NonNull
  public RequestCreator config(@NonNull Bitmap.Config config) {
    data.config(config);
    return this;
  }

  /**
   * Sets the stable key for this request to be used instead of the URI or resource ID when
   * caching. Two requests with the same value are considered to be for the same resource.
   */
  @NonNull
  public RequestCreator stableKey(@NonNull String stableKey) {
    data.stableKey(stableKey);
    return this;
  }

  /**
   * Set the priority of this request.
   * <p>
   * This will affect the order in which the requests execute but does not guarantee it.
   * By default, all requests have {@link Priority#NORMAL} priority, except for
   * {@link #fetch()} requests, which have {@link Priority#LOW} priority by default.
   */
  @NonNull
  public RequestCreator priority(@NonNull Priority priority) {
    data.priority(priority);
    return this;
  }

  /**
   * Add a custom transformation to be applied to the image.
   * <p>
   * Custom transformations will always be run after the built-in transformations.
   */
  // TODO show example of calling resize after a transform in the javadoc
  @NonNull
  public RequestCreator transform(@NonNull Transformation transformation) {
    data.transform(transformation);
    return this;
  }

  /**
   * Add a list of custom transformations to be applied to the image.
   * <p>
   * Custom transformations will always be run after the built-in transformations.
   */
  @NonNull
  public RequestCreator transform(@NonNull List<? extends Transformation> transformations) {
    data.transform(transformations);
    return this;
  }

  /**
   * Specifies the {@link MemoryPolicy} to use for this request. You may specify additional policy
   * options using the varargs parameter.
   */
  @NonNull
  public RequestCreator memoryPolicy(@NonNull MemoryPolicy policy,
      @NonNull MemoryPolicy... additional) {
    data.memoryPolicy(policy, additional);
    return this;
  }

  /**
   * Specifies the {@link NetworkPolicy} to use for this request. You may specify additional policy
   * options using the varargs parameter.
   */
  @NonNull
  public RequestCreator networkPolicy(@NonNull NetworkPolicy policy,
      @NonNull NetworkPolicy... additional) {
    data.networkPolicy(policy, additional);
    return this;
  }

  /**
   * Set inPurgeable and inInputShareable when decoding. This will force the bitmap to be decoded
   * from a byte array instead of a stream, since inPurgeable only affects the former.
   * <p>
   * <em>Note</em>: as of API level 21 (Lollipop), the inPurgeable field is deprecated and will be
   * ignored.
   */
  @NonNull
  public RequestCreator purgeable() {
    data.purgeable();
    return this;
  }

  /** Disable brief fade in of images loaded from the disk cache or network. */
  @NonNull
  public RequestCreator noFade() {
    noFade = true;
    return this;
  }

  /**
   * Synchronously fulfill this request. Must not be called from the main thread.
   */
  @Nullable // TODO make non-null and always throw?
  public Bitmap get() throws IOException {
    long started = System.nanoTime();
    checkNotMain();

    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with get.");
    }
    if (!data.hasImage()) {
      return null;
    }

    Request request = createRequest(started);

    Action action = new GetAction(picasso, request);
    RequestHandler.Result result =
        forRequest(picasso, picasso.dispatcher, picasso.cache, picasso.stats, action).hunt();
    Bitmap bitmap = result.getBitmap();
    if (bitmap != null && shouldWriteToMemoryCache(request.memoryPolicy)) {
      picasso.cache.set(request.key, bitmap);
    }
    return bitmap;
  }

  /**
   * Asynchronously fulfills the request without a {@link ImageView} or {@link BitmapTarget}. This
   * is useful when you want to warm up the cache with an image.
   * <p>
   * <em>Note:</em> It is safe to invoke this method from any thread.
   */
  public void fetch() {
    fetch(null);
  }

  /**
   * Asynchronously fulfills the request without a {@link ImageView} or {@link BitmapTarget},
   * and invokes the target {@link Callback} with the result. This is useful when you want to warm
   * up the cache with an image.
   * <p>
   * <em>Note:</em> The {@link Callback} param is a strong reference and will prevent your
   * {@link android.app.Activity} or {@link android.app.Fragment} from being garbage collected
   * until the request is completed.
   */
  public void fetch(@Nullable Callback callback) {
    long started = System.nanoTime();

    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with fetch.");
    }
    if (data.hasImage()) {
      // Fetch requests have lower priority by default.
      if (!data.hasPriority()) {
        data.priority(Priority.LOW);
      }

      Request request = createRequest(started);

      if (shouldReadFromMemoryCache(request.memoryPolicy)) {
        Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
        if (bitmap != null) {
          if (picasso.loggingEnabled) {
            log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + MEMORY);
          }
          if (callback != null) {
            callback.onSuccess();
          }
          return;
        }
      }

      Action action = new FetchAction(picasso, request, callback);
      picasso.submit(action);
    }
  }

  /**
   * Asynchronously fulfills the request into the specified {@link BitmapTarget}. In most cases, you
   * should use this when you are dealing with a custom {@link android.view.View View} or view
   * holder which should implement the {@link BitmapTarget} interface.
   * <p>
   * Implementing on a {@link android.view.View View}:
   * <blockquote><pre>
   * public class ProfileView extends FrameLayout implements Target {
   *   {@literal @}Override public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
   *     setBackgroundDrawable(new BitmapDrawable(bitmap));
   *   }
   *
   *   {@literal @}Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
   *     setBackgroundDrawable(errorDrawable);
   *   }
   *
   *   {@literal @}Override public void onPrepareLoad(Drawable placeHolderDrawable) {
   *     setBackgroundDrawable(placeHolderDrawable);
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
   *   {@literal @}Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
   *     frame.setBackgroundDrawable(errorDrawable);
   *   }
   *
   *   {@literal @}Override public void onPrepareLoad(Drawable placeHolderDrawable) {
   *     frame.setBackgroundDrawable(placeHolderDrawable);
   *   }
   * }
   * </pre></blockquote>
   * <p>
   * To receive callbacks when an image is loaded use
   * {@link #into(android.widget.ImageView, Callback)}.
   */
  public void into(@NonNull BitmapTarget target) {
    long started = System.nanoTime();
    checkMain();

    if (target == null) {
      throw new IllegalArgumentException("Target must not be null.");
    }
    if (deferred) {
      throw new IllegalStateException("Fit cannot be used with a Target.");
    }

    if (!data.hasImage()) {
      picasso.cancelRequest(target);
      target.onPrepareLoad(setPlaceholder ? getPlaceholderDrawable() : null);
      return;
    }

    Request request = createRequest(started);

    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
      if (bitmap != null) {
        picasso.cancelRequest(target);
        target.onBitmapLoaded(bitmap, MEMORY);
        return;
      }
    }

    target.onPrepareLoad(setPlaceholder ? getPlaceholderDrawable() : null);

    Action action = new BitmapTargetAction(picasso, target, request, errorDrawable, errorResId);
    picasso.enqueueAndSubmit(action);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into a {@link Notification}.
   */
  public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int notificationId,
      @NonNull Notification notification) {
    into(remoteViews, viewId, notificationId, notification, null);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into a {@link Notification}.
   */
  public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int notificationId,
      @NonNull Notification notification, @Nullable String notificationTag) {
    into(remoteViews, viewId, notificationId, notification, notificationTag, null);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into a {@link Notification}.
   */
  public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int notificationId,
      @NonNull Notification notification, @Nullable String notificationTag,
      @Nullable Callback callback) {
    long started = System.nanoTime();

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
    RemoteViewsAction action =
        new NotificationAction(picasso, request, errorResId,
            new RemoteViewsTarget(remoteViews, viewId), notificationId, notification,
            notificationTag, callback);

    performRemoteViewInto(request, action);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into all instances of a widget.
   */
  public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int appWidgetId) {
    into(remoteViews, viewId, new int[] { appWidgetId }, null);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into all instances of a widget.
   */
  public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId,
      @NonNull int[] appWidgetIds) {
    into(remoteViews, viewId, appWidgetIds, null);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into all instances of a widget.
   */
  public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int appWidgetId,
      @Nullable Callback callback) {
    into(remoteViews, viewId, new int[] { appWidgetId }, callback);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
   * given {@code viewId}. This is used for loading bitmaps into all instances of a widget.
   */
  public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, @NonNull int[] appWidgetIds,
      @Nullable Callback callback) {
    long started = System.nanoTime();

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
    RemoteViewsAction action =
        new AppWidgetAction(picasso, request, errorResId,
            new RemoteViewsTarget(remoteViews, viewId), appWidgetIds, callback);

    performRemoteViewInto(request, action);
  }

  /**
   * Asynchronously fulfills the request into the specified {@link ImageView}.
   * <p>
   * <em>Note:</em> This method will automatically support object recycling.
   */
  public void into(@NonNull ImageView target) {
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
  public void into(@NonNull ImageView target, @Nullable Callback callback) {
    long started = System.nanoTime();
    checkMain();

    if (target == null) {
      throw new IllegalArgumentException("Target must not be null.");
    }

    if (!data.hasImage()) {
      picasso.cancelRequest(target);
      if (setPlaceholder) {
        setPlaceholder(target, getPlaceholderDrawable());
      }
      return;
    }

    if (deferred) {
      if (data.hasSize()) {
        throw new IllegalStateException("Fit cannot be used with resize.");
      }
      int width = target.getWidth();
      int height = target.getHeight();
      if (width == 0 || height == 0) {
        if (setPlaceholder) {
          setPlaceholder(target, getPlaceholderDrawable());
        }
        picasso.defer(target, new DeferredRequestCreator(this, target, callback));
        return;
      }
      data.resize(width, height);
    }

    Request request = createRequest(started);

    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
      if (bitmap != null) {
        picasso.cancelRequest(target);
        RequestHandler.Result result = new RequestHandler.Result(bitmap, MEMORY);
        setResult(target, picasso.context, result, noFade, picasso.indicatorsEnabled);
        if (picasso.loggingEnabled) {
          log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + MEMORY);
        }
        if (callback != null) {
          callback.onSuccess();
        }
        return;
      }
    }

    if (setPlaceholder) {
      setPlaceholder(target, getPlaceholderDrawable());
    }

    Action action = new ImageViewAction(picasso, target, request, errorDrawable, errorResId, noFade,
        callback);
    picasso.enqueueAndSubmit(action);
  }

  private @Nullable Drawable getPlaceholderDrawable() {
    return placeholderResId == 0
        ? placeholderDrawable
        : ContextCompat.getDrawable(picasso.context, placeholderResId);
  }

  /** Create the request optionally passing it through the request transformer. */
  private Request createRequest(long started) {
    int id = nextId.getAndIncrement();

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

  private void performRemoteViewInto(Request request, RemoteViewsAction action) {
    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(action.request.key);
      if (bitmap != null) {
        action.complete(new RequestHandler.Result(bitmap, MEMORY));
        return;
      }
    }

    if (placeholderResId != 0) {
      action.setImageResource(placeholderResId);
    }

    picasso.enqueueAndSubmit(action);
  }
}
