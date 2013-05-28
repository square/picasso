package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ImageView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.content.ContentResolver.SCHEME_FILE;
import static android.provider.ContactsContract.Contacts;
import static com.squareup.picasso.Loader.Response;
import static com.squareup.picasso.Utils.calculateInSampleSize;

/**
 * Image downloading, transformation, and caching manager.
 * <p/>
 * Use {@link #with(android.content.Context)} for the global singleton instance or construct your
 * own instance with {@link Builder}.
 */
public class Picasso {
  private static final int RETRY_DELAY = 500;
  private static final int REQUEST_COMPLETE = 1;
  private static final int REQUEST_RETRY = 2;
  private static final int REQUEST_DECODE_FAILED = 3;

  public static final String SCHEME_RESOURCES = "picasso.resources";
  public static final String SCHEME_ASSETS = "picasso.assets";

  /**
   * Global lock for bitmap decoding to ensure that we are only are decoding one at a time. Since
   * this will only ever happen in background threads we help avoid excessive memory thrashing as
   * well as potential OOMs. Shamelessly stolen from Volley.
   */
  private static final Object DECODE_LOCK = new Object();

  /** Callbacks for Picasso events. */
  public interface Listener {
    /**
     * Invoked when an image has failed to load. This is useful for reporting image failures to a
     * remote analytics service, for example.
     */
    void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception);
  }

  // TODO This should be static.
  final Handler handler = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      Request request = (Request) msg.obj;
      if (request.future.isCancelled() || request.retryCancelled) {
        return;
      }

      Picasso picasso = request.picasso;
      switch (msg.what) {
        case REQUEST_COMPLETE:
          picasso.targetsToRequests.remove(request.getTarget());
          request.complete();
          break;

        case REQUEST_RETRY:
          picasso.retry(request);
          break;

        case REQUEST_DECODE_FAILED:
          picasso.error(request);
          break;

        default:
          throw new AssertionError("Unknown handler message received: " + msg.what);
      }
    }
  };

  static Picasso singleton = null;

  final Context context;
  final Loader loader;
  final ExecutorService service;
  final Cache cache;
  final Listener listener;
  final Stats stats;
  final Map<Object, Request> targetsToRequests;

  boolean debugging;

  Picasso(Context context, Loader loader, ExecutorService service, Cache cache, Listener listener,
      Stats stats) {
    this.context = context;
    this.loader = loader;
    this.service = service;
    this.cache = cache;
    this.listener = listener;
    this.stats = stats;
    this.targetsToRequests = new WeakHashMap<Object, Request>();
  }

  /** Cancel any existing requests for the specified target {@link ImageView}. */
  public void cancelRequest(ImageView view) {
    cancelExistingRequest(view, null);
  }

  /** Cancel and existing requests for the specified {@link Target} instance. */
  public void cancelRequest(Target target) {
    cancelExistingRequest(target, null);
  }

  /**
   * Start an image request using the specified URI.
   *
   * @see #load(File)
   * @see #load(String)
   * @see #load(int)
   */
  public RequestBuilder load(Uri uri) {
    return new RequestBuilder(this, uri);
  }

  /**
   * Start an image request using the specified path. This is a convenience method for calling
   * {@link #load(Uri)}.
   * <p>
   * This path may be a remote URL, file resource (prefixed with {@code file:}), content resource
   * (prefixed with {@code content:}), or android resource (prefixed with {@code
   * android.resource:}.
   *
   * @see #load(Uri)
   * @see #load(File)
   * @see #load(int)
   */
  public RequestBuilder load(String path) {
    if (path == null || path.trim().length() == 0) {
      throw new IllegalArgumentException("Path must not be empty.");
    }
    return load(Uri.parse(path));
  }

  /**
   * Start an image request using the specified image file. This is a convenience method for
   * calling {@link #load(Uri)}.
   *
   * @see #load(Uri)
   * @see #load(String)
   * @see #load(int)
   */
  public RequestBuilder load(File file) {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null.");
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
  public RequestBuilder load(int resourceId) {
    if (resourceId == 0) {
      throw new IllegalArgumentException("Resource ID must not be zero.");
    }
    return load(Utils.createResourceUri(context, resourceId));
  }

  /** {@code true} if debug display, logging, and statistics are enabled. */
  public boolean isDebugging() {
    return debugging;
  }

  /** Toggle whether debug display, logging, and statistics are enabled. */
  public void setDebugging(boolean debugging) {
    this.debugging = debugging;
  }

  /** Creates a {@link StatsSnapshot} of the current stats for this instance. */
  public StatsSnapshot getSnapshot() {
    return stats.createSnapshot();
  }

  void submit(Request request) {
    Object target = request.getTarget();
    if (target == null) return;

    cancelExistingRequest(target, request.uri);

    targetsToRequests.put(target, request);
    request.future = service.submit(request);
  }

  void run(Request request) {
    try {
      Bitmap result = resolveRequest(request);

      if (result == null) {
        handler.sendMessage(handler.obtainMessage(REQUEST_DECODE_FAILED, request));
        return;
      }

      request.result = result;
      handler.sendMessage(handler.obtainMessage(REQUEST_COMPLETE, request));
    } catch (IOException e) {
      if (listener != null && request.uri != null) {
        listener.onImageLoadFailed(this, request.uri, e);
      }
      handler.sendMessageDelayed(handler.obtainMessage(REQUEST_RETRY, request), RETRY_DELAY);
    }
  }

  Bitmap resolveRequest(Request request) throws IOException {
    Bitmap bitmap = loadFromCache(request);
    if (bitmap == null) {
      stats.cacheMiss();
      try {
        bitmap = loadFromType(request);
      } catch (OutOfMemoryError e) {
        throw new IOException("Failed to decode request: " + request, e);
      }

      if (bitmap != null && !request.skipCache) {
        cache.set(request.key, bitmap);
      }
    } else {
      stats.cacheHit();
    }
    return bitmap;
  }

  Bitmap quickMemoryCacheCheck(Object target, Uri uri, String key) {
    Bitmap cached = cache.get(key);
    cancelExistingRequest(target, uri);

    if (cached != null) {
      stats.cacheHit();
    }

    return cached;
  }

  void retry(Request request) {
    if (request.retryCancelled) return;

    if (request.retryCount > 0) {
      request.retryCount--;
      submit(request);
    } else {
      targetsToRequests.remove(request.getTarget());
      request.error();
    }
  }

  void error(Request request) {
    targetsToRequests.remove(request.getTarget());
    request.error();
  }

  Bitmap decodeStream(InputStream stream, PicassoBitmapOptions bitmapOptions) {
    if (stream == null) return null;
    if (bitmapOptions != null) {
      // Ensure we are not doing only a bounds decode.
      bitmapOptions.inJustDecodeBounds = false;
    }
    return BitmapFactory.decodeStream(stream, null, bitmapOptions);
  }

  Bitmap decodeContentStream(Uri path, PicassoBitmapOptions bitmapOptions) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeStream(contentResolver.openInputStream(path), null, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    return BitmapFactory.decodeStream(contentResolver.openInputStream(path), null, bitmapOptions);
  }

  Bitmap decodeAsset(Uri uri, PicassoBitmapOptions bitmapOptions) throws IOException {
    String path = uri.getPath().substring(1);
    AssetManager assets = context.getAssets();
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeStream(assets.open(path), null, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    return decodeStream(assets.open(path), bitmapOptions);
  }

  Bitmap decodeResource(Uri uri, PicassoBitmapOptions bitmapOptions) throws IOException {
    String path = uri.getPath().substring(1);
    try {
      return decodeResource(Integer.parseInt(path), bitmapOptions);
    } catch (NumberFormatException exception) {
      throw new IOException("\"" + path + "\" isn't a valid resource id", exception);
    }
  }

  Bitmap decodeResource(int resourceId, PicassoBitmapOptions bitmapOptions) {
    Resources resources = context.getResources();
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    return BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
  }

  private void cancelExistingRequest(Object target, Uri uri) {
    Request existing = targetsToRequests.remove(target);
    if (existing != null) {
      if (!existing.future.isDone()) {
        existing.future.cancel(true);
      } else if (uri == null || !uri.equals(existing.uri)) {
        existing.retryCancelled = true;
      }
    }
  }

  private Bitmap loadFromCache(Request request) {
    if (request.skipCache) return null;

    Bitmap cached = cache.get(request.key);
    if (cached != null) {
      request.loadedFrom = Request.LoadedFrom.MEMORY;
    }
    return cached;
  }

  private Bitmap loadFromType(Request request) throws IOException {
    PicassoBitmapOptions options = request.options;

    int exifRotation = 0;
    Bitmap result = null;

    Uri uri = request.uri;
    String scheme = uri.getScheme();

    if (SCHEME_CONTENT.equals(scheme)) {
      ContentResolver contentResolver = context.getContentResolver();
      if (Contacts.CONTENT_URI.getHost().equals(uri.getHost()) //
          && !uri.getPathSegments().contains(Contacts.Photo.CONTENT_DIRECTORY)) {
        InputStream contactStream = Utils.getContactPhotoStream(contentResolver, uri);
        result = decodeStream(contactStream, options);
      } else {
        exifRotation = Utils.getContentProviderExifRotation(contentResolver, uri);
        result = decodeContentStream(uri, options);
      }
      request.loadedFrom = Request.LoadedFrom.DISK;
    } else if (SCHEME_FILE.equals(scheme)) {
      exifRotation = Utils.getFileExifRotation(uri.getPath());
      result = decodeContentStream(uri, options);
      request.loadedFrom = Request.LoadedFrom.DISK;
    } else if (SCHEME_ANDROID_RESOURCE.equals(scheme)) {
      result = decodeContentStream(uri, options);
      request.loadedFrom = Request.LoadedFrom.DISK;
    } else if (SCHEME_ASSETS.equals(scheme)) {
      result = decodeAsset(uri, options);
      request.loadedFrom = Request.LoadedFrom.DISK;
    } else if (SCHEME_RESOURCES.equals(scheme)) {
      result = decodeResource(uri, options);
      request.loadedFrom = Request.LoadedFrom.DISK;
    } else {
      Response response = null;
      try {
        response = loader.load(uri, request.retryCount == 0);
        if (response == null) {
          return null;
        }
        result = decodeStream(response.stream, options);
      } finally {
        if (response != null && response.stream != null) {
          try {
            response.stream.close();
          } catch (IOException ignored) {
          }
        }
      }
      request.loadedFrom = response.cached ? Request.LoadedFrom.DISK : Request.LoadedFrom.NETWORK;
    }

    if (result == null) {
      return null;
    }

    stats.bitmapDecoded(result);

    // If the caller wants deferred resize, try to load the target ImageView's measured size.
    if (options != null && options.deferredResize) {
      ImageView target = request.target.get();
      if (target != null) {
        int targetWidth = target.getMeasuredWidth();
        int targetHeight = target.getMeasuredHeight();
        if (targetWidth != 0 && targetHeight != 0) {
          options.targetWidth = targetWidth;
          options.targetHeight = targetHeight;
        }
      }
    }

    if (options != null || exifRotation != 0) {
      result = transformResult(options, result, exifRotation);
    }

    List<Transformation> transformations = request.transformations;
    if (transformations != null) {
      result = applyCustomTransformations(transformations, result);
      stats.bitmapTransformed(result);
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

    synchronized (DECODE_LOCK) {
      Bitmap newResult =
          Bitmap.createBitmap(result, drawX, drawY, drawWidth, drawHeight, matrix, false);
      if (newResult != result) {
        result.recycle();
        result = newResult;
      }
    }

    return result;
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

  /**
   * The global default {@link Picasso} instance.
   * <p>
   * This instance is automatically initialized with defaults that are suitable to most
   * implementations.
   * <ul>
   * <li>LRU memory cache of 15% the available application RAM up to 20MB</li>
   * <li>Disk cache of 2% storage space up to 50MB but no less than 5MB. (Note: this is only
   * available on API 14+ <em>or</em> if you are using a standalone library that provides a disk
   * cache on all API levels like OkHttp)</li>
   * <li>Three download threads for disk and network access.</li>
   * </ul>
   * <p>
   * If these settings do not meet the requirements of your application you can construct your own
   * instance with full control over the configuration by using {@link Picasso.Builder}.
   */
  public static Picasso with(Context context) {
    if (singleton == null) {
      singleton = new Builder(context).build();
    }
    return singleton;
  }

  /** Fluent API for creating {@link Picasso} instances. */
  @SuppressWarnings("UnusedDeclaration") // Public API.
  public static class Builder {
    private final Context context;
    private Loader loader;
    private ExecutorService service;
    private Cache memoryCache;
    private Listener listener;

    /** Start building a new {@link Picasso} instance. */
    public Builder(Context context) {
      if (context == null) {
        throw new IllegalArgumentException("Context must not be null.");
      }
      this.context = context.getApplicationContext();
    }

    /** Specify the {@link Loader} that will be used for downloading images. */
    public Builder loader(Loader loader) {
      if (loader == null) {
        throw new IllegalArgumentException("Loader must not be null.");
      }
      if (this.loader != null) {
        throw new IllegalStateException("Loader already set.");
      }
      this.loader = loader;
      return this;
    }

    /** Specify the executor service for loading images in the background. */
    public Builder executor(ExecutorService executorService) {
      if (executorService == null) {
        throw new IllegalArgumentException("Executor service must not be null.");
      }
      if (this.service != null) {
        throw new IllegalStateException("Executor service already set.");
      }
      this.service = executorService;
      return this;
    }

    /** Specify the memory cache used for the most recent images. */
    public Builder memoryCache(Cache memoryCache) {
      if (memoryCache == null) {
        throw new IllegalArgumentException("Memory cache must not be null.");
      }
      if (this.memoryCache != null) {
        throw new IllegalStateException("Memory cache already set.");
      }
      this.memoryCache = memoryCache;
      return this;
    }

    /** Specify a listener for interesting events. */
    public Builder listener(Listener listener) {
      if (listener == null) {
        throw new IllegalArgumentException("Listener must not be null.");
      }
      if (this.listener != null) {
        throw new IllegalStateException("Listener already set.");
      }
      this.listener = listener;
      return this;
    }

    /** Create the {@link Picasso} instance. */
    public Picasso build() {
      Context context = this.context;

      if (loader == null) {
        loader = Utils.createDefaultLoader(context);
      }
      if (memoryCache == null) {
        memoryCache = new LruCache(context);
      }
      if (service == null) {
        service = Executors.newFixedThreadPool(3, new Utils.PicassoThreadFactory());
      }

      Stats stats = new Stats(memoryCache);

      return new Picasso(context, loader, service, memoryCache, listener, stats);
    }
  }
}
