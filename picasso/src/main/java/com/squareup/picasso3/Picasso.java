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

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import okhttp3.Call;
import okhttp3.OkHttpClient;

import static com.squareup.picasso3.Dispatcher.HUNTER_COMPLETE;
import static com.squareup.picasso3.Dispatcher.REQUEST_BATCH_RESUME;
import static com.squareup.picasso3.MemoryPolicy.shouldReadFromMemoryCache;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso3.Utils.OWNER_MAIN;
import static com.squareup.picasso3.Utils.VERB_COMPLETED;
import static com.squareup.picasso3.Utils.VERB_ERRORED;
import static com.squareup.picasso3.Utils.VERB_RESUMED;
import static com.squareup.picasso3.Utils.calculateDiskCacheSize;
import static com.squareup.picasso3.Utils.checkMain;
import static com.squareup.picasso3.Utils.checkNotNull;
import static com.squareup.picasso3.Utils.createDefaultCacheDir;
import static com.squareup.picasso3.Utils.log;

/**
 * Image downloading, transformation, and caching manager.
 * <p>
 * Use {@see PicassoProvider#get()} for a global singleton instance
 * or construct your own instance with {@link Builder}.
 */
public class Picasso implements LifecycleObserver {

  /** Callbacks for Picasso events. */
  public interface Listener {
    /**
     * Invoked when an image has failed to load. This is useful for reporting image failures to a
     * remote analytics service, for example.
     */
    void onImageLoadFailed(@NonNull Picasso picasso, @NonNull Uri uri,
        @NonNull Exception exception);
  }

  /**
   * A transformer that is called immediately before every request is submitted. This can be used to
   * modify any information about a request.
   * <p>
   * For example, if you use a CDN you can change the hostname for the image based on the current
   * location of the user in order to get faster download speeds.
   */
  public interface RequestTransformer {
    /**
     * Transform a request before it is submitted to be processed.
     *
     * @return The original request or a new request to replace it. Must not be null.
     */
    @NonNull Request transformRequest(@NonNull Request request);
  }

  /**
   * The priority of a request.
   *
   * @see RequestCreator#priority(Priority)
   */
  public enum Priority {
    LOW,
    NORMAL,
    HIGH
  }

  static final String TAG = "Picasso";
  static final Handler HANDLER = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      switch (msg.what) {
        case HUNTER_COMPLETE: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          hunter.picasso.complete(hunter);
          break;
        }
        case REQUEST_BATCH_RESUME:
          @SuppressWarnings("unchecked") List<Action> batch = (List<Action>) msg.obj;
          //noinspection ForLoopReplaceableByForEach
          for (int i = 0, n = batch.size(); i < n; i++) {
            Action action = batch.get(i);
            action.picasso.resumeAction(action);
          }
          break;
        default:
          throw new AssertionError("Unknown handler message received: " + msg.what);
      }
    }
  };

  @Nullable final Listener listener;
  final List<RequestTransformer> requestTransformers;
  final List<RequestHandler> requestHandlers;

  final Context context;
  final Dispatcher dispatcher;
  final Call.Factory callFactory;
  private final @Nullable okhttp3.Cache closeableCache;
  final PlatformLruCache cache;
  final Stats stats;
  final Map<Object, Action> targetToAction;
  final Map<ImageView, DeferredRequestCreator> targetToDeferredRequestCreator;
  @Nullable final Bitmap.Config defaultBitmapConfig;

  boolean indicatorsEnabled;
  volatile boolean loggingEnabled;

  boolean shutdown;

  Picasso(Context context, Dispatcher dispatcher, Call.Factory callFactory,
      @Nullable okhttp3.Cache closeableCache, PlatformLruCache cache, @Nullable Listener listener,
      List<RequestTransformer> requestTransformers, List<RequestHandler> extraRequestHandlers,
      Stats stats, @Nullable Bitmap.Config defaultBitmapConfig, boolean indicatorsEnabled,
      boolean loggingEnabled) {
    this.context = context;
    this.dispatcher = dispatcher;
    this.callFactory = callFactory;
    this.closeableCache = closeableCache;
    this.cache = cache;
    this.listener = listener;
    this.requestTransformers = Collections.unmodifiableList(new ArrayList<>(requestTransformers));
    this.defaultBitmapConfig = defaultBitmapConfig;

    // Adjust this and Builder(Picasso) as internal handlers are added or removed.
    int builtInHandlers = 8;
    int extraCount = extraRequestHandlers.size();
    List<RequestHandler> allRequestHandlers = new ArrayList<>(builtInHandlers + extraCount);

    // ResourceRequestHandler needs to be the first in the list to avoid
    // forcing other RequestHandlers to perform null checks on request.uri
    // to cover the (request.resourceId != 0) case.
    allRequestHandlers.add(ResourceDrawableRequestHandler.create(context));
    allRequestHandlers.add(new ResourceRequestHandler(context));
    allRequestHandlers.addAll(extraRequestHandlers);
    allRequestHandlers.add(new ContactsPhotoRequestHandler(context));
    allRequestHandlers.add(new MediaStoreRequestHandler(context));
    allRequestHandlers.add(new ContentStreamRequestHandler(context));
    allRequestHandlers.add(new AssetRequestHandler(context));
    allRequestHandlers.add(new FileRequestHandler(context));
    allRequestHandlers.add(new NetworkRequestHandler(callFactory, stats));
    requestHandlers = Collections.unmodifiableList(allRequestHandlers);

    this.stats = stats;
    this.targetToAction = new LinkedHashMap<>();
    this.targetToDeferredRequestCreator = new LinkedHashMap<>();
    this.indicatorsEnabled = indicatorsEnabled;
    this.loggingEnabled = loggingEnabled;
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  void cancelAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = actions.size(); i < n; i++) {
      Action action = actions.get(i);
      cancelExistingRequest(action.getTarget());
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = deferredRequestCreators.size(); i < n; i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      deferredRequestCreator.cancel();
    }
  }

  /** Cancel any existing requests for the specified target {@link ImageView}. */
  public void cancelRequest(@NonNull ImageView view) {
    // checkMain() is called from cancelExistingRequest()
    checkNotNull(view, "view == null");
    cancelExistingRequest(view);
  }

  /** Cancel any existing requests for the specified {@link BitmapTarget} instance. */
  public void cancelRequest(@NonNull BitmapTarget target) {
    // checkMain() is called from cancelExistingRequest()
    checkNotNull(target, "target == null");
    cancelExistingRequest(target);
  }

  /**
   * Cancel any existing requests for the specified {@link RemoteViews} target with the given {@code
   * viewId}.
   */
  public void cancelRequest(@NonNull RemoteViews remoteViews, @IdRes int viewId) {
    // checkMain() is called from cancelExistingRequest()
    checkNotNull(remoteViews, "remoteViews == null");
    cancelExistingRequest(new RemoteViewsAction.RemoteViewsTarget(remoteViews, viewId));
  }

  /**
   * Cancel any existing requests with given tag. You can set a tag
   * on new requests with {@link RequestCreator#tag(Object)}.
   *
   * @see RequestCreator#tag(Object)
   */
  public void cancelTag(@NonNull Object tag) {
    checkMain();
    checkNotNull(tag, "tag == null");

    List<Action> actions = new ArrayList<>(targetToAction.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = actions.size(); i < n; i++) {
      Action action = actions.get(i);
      if (tag.equals(action.getTag())) {
        cancelExistingRequest(action.getTarget());
      }
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = deferredRequestCreators.size(); i < n; i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      if (tag.equals(deferredRequestCreator.getTag())) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  void pauseAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = actions.size(); i < n; i++) {
      Action action = actions.get(i);
      dispatcher.dispatchPauseTag(action.getTag());
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = deferredRequestCreators.size(); i < n; i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      Object tag = deferredRequestCreator.getTag();
      if (tag != null) {
        dispatcher.dispatchPauseTag(tag);
      }
    }
  }

  /**
   * Pause existing requests with the given tag. Use {@link #resumeTag(Object)}
   * to resume requests with the given tag.
   *
   * @see #resumeTag(Object)
   * @see RequestCreator#tag(Object)
   */
  public void pauseTag(@NonNull Object tag) {
    checkNotNull(tag, "tag == null");
    dispatcher.dispatchPauseTag(tag);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  void resumeAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = actions.size(); i < n; i++) {
      Action action = actions.get(i);
      dispatcher.dispatchResumeTag(action.getTag());
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = deferredRequestCreators.size(); i < n; i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      Object tag = deferredRequestCreator.getTag();
      if (tag != null) {
        dispatcher.dispatchResumeTag(tag);
      }
    }
  }

  /**
   * Resume paused requests with the given tag. Use {@link #pauseTag(Object)}
   * to pause requests with the given tag.
   *
   * @see #pauseTag(Object)
   * @see RequestCreator#tag(Object)
   */
  public void resumeTag(@NonNull Object tag) {
    checkNotNull(tag, "tag == null");
    dispatcher.dispatchResumeTag(tag);
  }

  /**
   * Start an image request using the specified URI.
   * <p>
   * Passing {@code null} as a {@code uri} will not trigger any request but will set a placeholder,
   * if one is specified.
   *
   * @see #load(File)
   * @see #load(String)
   * @see #load(int)
   */
  @NonNull
  public RequestCreator load(@Nullable Uri uri) {
    return new RequestCreator(this, uri, 0);
  }

  /**
   * Start an image request using the specified path. This is a convenience method for calling
   * {@link #load(Uri)}.
   * <p>
   * This path may be a remote URL, file resource (prefixed with {@code file:}), content resource
   * (prefixed with {@code content:}), or android resource (prefixed with {@code android.resource:}.
   * <p>
   * Passing {@code null} as a {@code path} will not trigger any request but will set a
   * placeholder, if one is specified.
   *
   * @throws IllegalArgumentException if {@code path} is empty or blank string.
   * @see #load(Uri)
   * @see #load(File)
   * @see #load(int)
   */
  @NonNull
  public RequestCreator load(@Nullable String path) {
    if (path == null) {
      return new RequestCreator(this, null, 0);
    }
    if (path.trim().length() == 0) {
      throw new IllegalArgumentException("Path must not be empty.");
    }
    return load(Uri.parse(path));
  }

  /**
   * Start an image request using the specified image file. This is a convenience method for
   * calling {@link #load(Uri)}.
   * <p>
   * Passing {@code null} as a {@code file} will not trigger any request but will set a
   * placeholder, if one is specified.
   * <p>
   * Equivalent to calling {@link #load(Uri) load(Uri.fromFile(file))}.
   *
   * @see #load(Uri)
   * @see #load(String)
   * @see #load(int)
   */
  @NonNull
  public RequestCreator load(@Nullable File file) {
    if (file == null) {
      return new RequestCreator(this, null, 0);
    }
    return load(Uri.fromFile(file));
  }

  /**
   * Start an image request using the specified drawable resource ID.
   *
   * @see #load(Uri)
   * @see #load(String)
   * @see #load(File)
   */
  @NonNull
  public RequestCreator load(@DrawableRes int resourceId) {
    if (resourceId == 0) {
      throw new IllegalArgumentException("Resource ID must not be zero.");
    }
    return new RequestCreator(this, null, resourceId);
  }

  /**
   * Clear all the bitmaps from the memory cache.
   */
  public void evictAll() {
    cache.clear();
  }

  /**
   * Invalidate all memory cached images for the specified {@code uri}.
   *
   * @see #invalidate(String)
   * @see #invalidate(File)
   */
  public void invalidate(@Nullable Uri uri) {
    if (uri != null) {
      cache.clearKeyUri(uri.toString());
    }
  }

  /**
   * Invalidate all memory cached images for the specified {@code path}. You can also pass a
   * {@linkplain RequestCreator#stableKey stable key}.
   *
   * @see #invalidate(Uri)
   * @see #invalidate(File)
   */
  public void invalidate(@Nullable String path) {
    if (path != null) {
      invalidate(Uri.parse(path));
    }
  }

  /**
   * Invalidate all memory cached images for the specified {@code file}.
   *
   * @see #invalidate(Uri)
   * @see #invalidate(String)
   */
  public void invalidate(@NonNull File file) {
    checkNotNull(file, "file == null");
    invalidate(Uri.fromFile(file));
  }

  /** Toggle whether to display debug indicators on images. */
  @SuppressWarnings("UnusedDeclaration") public void setIndicatorsEnabled(boolean enabled) {
    indicatorsEnabled = enabled;
  }

  /** {@code true} if debug indicators should are displayed on images. */
  @SuppressWarnings("UnusedDeclaration") public boolean getIndicatorsEnabled() {
    return indicatorsEnabled;
  }

  /**
   * Toggle whether debug logging is enabled.
   * <p>
   * <b>WARNING:</b> Enabling this will result in excessive object allocation. This should be only
   * be used for debugging Picasso behavior. Do NOT pass {@code BuildConfig.DEBUG}.
   */
  @SuppressWarnings("UnusedDeclaration") // Public API.
  public void setLoggingEnabled(boolean enabled) {
    loggingEnabled = enabled;
  }

  /** {@code true} if debug logging is enabled. */
  public boolean isLoggingEnabled() {
    return loggingEnabled;
  }

  /**
   * Creates a {@link StatsSnapshot} of the current stats for this instance.
   * <p>
   * <b>NOTE:</b> The snapshot may not always be completely up-to-date if requests are still in
   * progress.
   */
  @NonNull
  @SuppressWarnings("UnusedDeclaration") public StatsSnapshot getSnapshot() {
    return stats.createSnapshot();
  }

  /** Stops this instance from accepting further requests. */
  public void shutdown() {
    if (shutdown) {
      return;
    }
    cache.clear();
    stats.shutdown();
    dispatcher.shutdown();
    if (closeableCache != null) {
      try {
        closeableCache.close();
      } catch (IOException ignored) {
      }
    }
    for (DeferredRequestCreator deferredRequestCreator : targetToDeferredRequestCreator.values()) {
      deferredRequestCreator.cancel();
    }
    targetToDeferredRequestCreator.clear();
    shutdown = true;
  }

  List<RequestHandler> getRequestHandlers() {
    return requestHandlers;
  }

  Request transformRequest(Request request) {
    for (int i = 0, size = requestTransformers.size(); i < size; i++) {
      RequestTransformer transformer = requestTransformers.get(i);
      Request transformed = transformer.transformRequest(request);
      if (transformed == null) {
        throw new IllegalStateException("Request transformer "
            + transformer.getClass().getCanonicalName()
            + " returned null for "
            + request);
      }
      request = transformed;
    }

    return request;
  }

  void defer(ImageView view, DeferredRequestCreator request) {
    // If there is already a deferred request, cancel it.
    if (targetToDeferredRequestCreator.containsKey(view)) {
      cancelExistingRequest(view);
    }
    targetToDeferredRequestCreator.put(view, request);
  }

  void enqueueAndSubmit(Action action) {
    Object target = action.getTarget();
    if (targetToAction.get(target) != action) {
      // This will also check we are on the main thread.
      cancelExistingRequest(target);
      targetToAction.put(target, action);
    }
    submit(action);
  }

  void submit(Action action) {
    dispatcher.dispatchSubmit(action);
  }

  @Nullable Bitmap quickMemoryCacheCheck(String key) {
    Bitmap cached = cache.get(key);
    if (cached != null) {
      stats.dispatchCacheHit();
    } else {
      stats.dispatchCacheMiss();
    }
    return cached;
  }

  void complete(BitmapHunter hunter) {
    Action single = hunter.getAction();
    List<Action> joined = hunter.getActions();

    boolean hasMultiple = joined != null && !joined.isEmpty();
    boolean shouldDeliver = single != null || hasMultiple;

    if (!shouldDeliver) {
      return;
    }

    Uri uri = checkNotNull(hunter.getData().uri, "uri == null");
    Exception exception = hunter.getException();
    RequestHandler.Result result = hunter.getResult();

    if (single != null) {
      deliverAction(result, single, exception);
    }

    if (joined != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, n = joined.size(); i < n; i++) {
        Action join = joined.get(i);
        deliverAction(result, join, exception);
      }
    }

    if (listener != null && exception != null) {
      listener.onImageLoadFailed(this, uri, exception);
    }
  }

  void resumeAction(Action action) {
    Bitmap bitmap = null;
    if (shouldReadFromMemoryCache(action.request.memoryPolicy)) {
      bitmap = quickMemoryCacheCheck(action.request.key);
    }

    if (bitmap != null) {
      // Resumed action is cached, complete immediately.
      deliverAction(new RequestHandler.Result(bitmap, MEMORY), action, null);
      if (loggingEnabled) {
        log(OWNER_MAIN, VERB_COMPLETED, action.request.logId(), "from " + MEMORY);
      }
    } else {
      // Re-submit the action to the executor.
      enqueueAndSubmit(action);
      if (loggingEnabled) {
        log(OWNER_MAIN, VERB_RESUMED, action.request.logId());
      }
    }
  }

  private void deliverAction(@Nullable RequestHandler.Result result, Action action,
      @Nullable Exception e) {
    if (action.cancelled) {
      return;
    }
    if (!action.willReplay) {
      targetToAction.remove(action.getTarget());
    }
    if (result != null) {
      action.complete(result);
      if (loggingEnabled) {
        log(OWNER_MAIN, VERB_COMPLETED, action.request.logId(), "from " + result.getLoadedFrom());
      }
    } else {
      Exception exception = checkNotNull(e, "e == null");
      action.error(exception);
      if (loggingEnabled) {
        log(OWNER_MAIN, VERB_ERRORED, action.request.logId(), exception.getMessage());
      }
    }
  }

  void cancelExistingRequest(Object target) {
    checkMain();
    Action action = targetToAction.remove(target);
    if (action != null) {
      action.cancel();
      dispatcher.dispatchCancel(action);
    }
    if (target instanceof ImageView) {
      ImageView targetImageView = (ImageView) target;
      DeferredRequestCreator deferredRequestCreator =
          targetToDeferredRequestCreator.remove(targetImageView);
      if (deferredRequestCreator != null) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @NonNull
  public Builder newBuilder() {
    return new Builder(this);
  }

  /** Fluent API for creating {@link Picasso} instances. */
  @SuppressWarnings("UnusedDeclaration") // Public API.
  public static class Builder {
    private final Context context;
    @Nullable private Call.Factory callFactory;
    @Nullable private ExecutorService service;
    @Nullable private PlatformLruCache cache;
    @Nullable private Listener listener;
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();
    private final List<RequestHandler> requestHandlers = new ArrayList<>();
    @Nullable private Bitmap.Config defaultBitmapConfig;

    private boolean indicatorsEnabled;
    private boolean loggingEnabled;

    /** Start building a new {@link Picasso} instance. */
    public Builder(@NonNull Context context) {
      checkNotNull(context, "context == null");
      this.context = context.getApplicationContext();
    }

    Builder(Picasso picasso) {
      context = picasso.context;
      callFactory = picasso.callFactory;
      service = picasso.dispatcher.service;
      cache = picasso.cache;
      listener = picasso.listener;
      requestTransformers.addAll(picasso.requestTransformers);
      // See Picasso(). Removes internal request handlers added before and after custom handlers.
      int numRequestHandlers = picasso.requestHandlers.size();
      requestHandlers.addAll(picasso.requestHandlers.subList(2, numRequestHandlers - 6));

      defaultBitmapConfig = picasso.defaultBitmapConfig;
      indicatorsEnabled = picasso.indicatorsEnabled;
      loggingEnabled = picasso.loggingEnabled;
    }

    /**
     * Specify the default {@link Bitmap.Config} used when decoding images. This can be overridden
     * on a per-request basis using {@link RequestCreator#config(Bitmap.Config) config(..)}.
     */
    @NonNull
    public Builder defaultBitmapConfig(@NonNull Bitmap.Config bitmapConfig) {
      checkNotNull(bitmapConfig, "bitmapConfig == null");
      this.defaultBitmapConfig = bitmapConfig;
      return this;
    }

    /**
     * Specify the HTTP client to be used for network requests.
     * <p>
     * Note: Calling {@link #callFactory} overwrites this value.
     */
    @NonNull
    public Builder client(@NonNull OkHttpClient client) {
      checkNotNull(client, "client == null");
      callFactory = client;
      return this;
    }

    /**
     * Specify the call factory to be used for network requests.
     * <p>
     * Note: Calling {@link #client} overwrites this value.
     */
    @NonNull
    public Builder callFactory(@NonNull Call.Factory factory) {
      checkNotNull(factory, "factory == null");
      callFactory = factory;
      return this;
    }

    /**
     * Specify the executor service for loading images in the background.
     * <p>
     * Note: Calling {@link Picasso#shutdown() shutdown()} will not shutdown supplied executors.
     */
    @NonNull
    public Builder executor(@NonNull ExecutorService executorService) {
      checkNotNull(executorService, "executorService == null");
      this.service = executorService;
      return this;
    }

    /**
     * Specify the memory cache size in bytes to use for the most recent images.
     * A size of 0 disables in-memory caching.
     */
    @NonNull
    public Builder withCacheSize(int maxByteCount) {
      if (maxByteCount < 0) {
        throw new IllegalArgumentException("maxByteCount < 0: " + maxByteCount);
      }
      cache = new PlatformLruCache(maxByteCount);
      return this;
    }

    /** Specify a listener for interesting events. */
    @NonNull
    public Builder listener(@NonNull Listener listener) {
      checkNotNull(listener, "listener == null");
      this.listener = listener;
      return this;
    }

    /** Add a transformer that observes and potentially modify all incoming requests. */
    @NonNull
    public Builder addRequestTransformer(@NonNull RequestTransformer transformer) {
      checkNotNull(transformer, "transformer == null");
      requestTransformers.add(transformer);
      return this;
    }

    /** Register a {@link RequestHandler}. */
    @NonNull
    public Builder addRequestHandler(@NonNull RequestHandler requestHandler) {
      checkNotNull(requestHandler, "requestHandler == null");
      requestHandlers.add(requestHandler);
      return this;
    }

    /** Toggle whether to display debug indicators on images. */
    @NonNull
    public Builder indicatorsEnabled(boolean enabled) {
      this.indicatorsEnabled = enabled;
      return this;
    }

    /**
     * Toggle whether debug logging is enabled.
     * <p>
     * <b>WARNING:</b> Enabling this will result in excessive object allocation. This should be only
     * be used for debugging purposes. Do NOT pass {@code BuildConfig.DEBUG}.
     */
    @NonNull
    public Builder loggingEnabled(boolean enabled) {
      this.loggingEnabled = enabled;
      return this;
    }

    /** Create the {@link Picasso} instance. */
    @NonNull
    public Picasso build() {
      Context context = this.context;

      okhttp3.Cache unsharedCache = null;
      if (callFactory == null) {
        File cacheDir = createDefaultCacheDir(context);
        long maxSize = calculateDiskCacheSize(cacheDir);
        unsharedCache = new okhttp3.Cache(cacheDir, maxSize);
        callFactory = new OkHttpClient.Builder()
            .cache(unsharedCache)
            .build();
      }
      if (cache == null) {
        cache = new PlatformLruCache(Utils.calculateMemoryCacheSize(context));
      }
      if (service == null) {
        service = new PicassoExecutorService();
      }

      Stats stats = new Stats(cache);

      Dispatcher dispatcher = new Dispatcher(context, service, HANDLER, cache, stats);

      return new Picasso(context, dispatcher, callFactory, unsharedCache, cache, listener,
          requestTransformers, requestHandlers, stats, defaultBitmapConfig, indicatorsEnabled,
          loggingEnabled);
    }
  }

  /** Describes where the image was loaded from. */
  public enum LoadedFrom {
    MEMORY(Color.GREEN),
    DISK(Color.BLUE),
    NETWORK(Color.RED);

    final int debugColor;

    LoadedFrom(int debugColor) {
      this.debugColor = debugColor;
    }
  }
}
