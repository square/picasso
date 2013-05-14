package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
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

import static com.squareup.picasso.Loader.Response;
import static com.squareup.picasso.Request.Type;
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

  private static final String FILE_SCHEME = "file:";
  private static final String CONTENT_SCHEME = "content:";

  /**
   * Global lock for bitmap decoding to ensure that we are only are decoding one at a time. Since
   * this will only ever happen in background threads we help avoid excessive memory thrashing as
   * well as potential OOMs. Shamelessly stolen from Volley.
   */
  private static final Object DECODE_LOCK = new Object();

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
          picasso.targetsToRequests.remove(request.getTarget());
          request.error();
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
  final Map<Object, Request> targetsToRequests;
  final Stats stats;

  boolean debugging;

  Picasso(Context context, Loader loader, ExecutorService service, Cache cache, Stats stats) {
    this.context = context;
    this.loader = loader;
    this.service = service;
    this.cache = cache;
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
   * Start an image request using the specified path. This path may be a remote URL, file resource,
   * or content provider resource.
   */
  public RequestBuilder load(String path) {
    if (path == null || path.trim().length() == 0) {
      throw new IllegalArgumentException("Path must not be empty.");
    }
    if (path.startsWith(FILE_SCHEME)) {
      return new RequestBuilder(this, Uri.parse(path).getPath(), Type.FILE);
    }
    if (path.startsWith(CONTENT_SCHEME)) {
      return new RequestBuilder(this, path, Type.CONTENT);
    }
    return new RequestBuilder(this, path, Type.STREAM);
  }

  /** Start an image request using the specified image file. */
  public RequestBuilder load(File file) {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null.");
    }
    return new RequestBuilder(this, file.getPath(), Type.FILE);
  }

  /** Start an image request using the specified drawable resource ID. */
  public RequestBuilder load(int resourceId) {
    if (resourceId == 0) {
      throw new IllegalArgumentException("Resource ID must not be zero.");
    }
    return new RequestBuilder(this, resourceId);
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

    cancelExistingRequest(target, request.path);

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
      handler.sendMessageDelayed(handler.obtainMessage(REQUEST_RETRY, request), RETRY_DELAY);
    }
  }

  Bitmap resolveRequest(Request request) throws IOException {
    Bitmap bitmap = loadFromCache(request);
    if (bitmap == null) {
      stats.cacheMiss();
      bitmap = loadFromType(request);

      if (bitmap != null && !request.skipCache) {
        cache.set(request.key, bitmap);
      }
    } else {
      stats.cacheHit();
    }
    return bitmap;
  }

  Bitmap quickMemoryCacheCheck(Object target, String key) {
    Bitmap cached = cache.get(key);
    cancelExistingRequest(target, key);

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

  Bitmap decodeStream(InputStream stream, PicassoBitmapOptions bitmapOptions) {
    if (stream == null) return null;
    synchronized (DECODE_LOCK) {
      return BitmapFactory.decodeStream(stream, null, bitmapOptions);
    }
  }

  Bitmap decodeContentStream(Uri path, PicassoBitmapOptions bitmapOptions) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeStream(contentResolver.openInputStream(path), null, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    synchronized (DECODE_LOCK) {
      return BitmapFactory.decodeStream(contentResolver.openInputStream(path), null, bitmapOptions);
    }
  }

  Bitmap decodeFile(String path, PicassoBitmapOptions bitmapOptions) {
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeFile(path, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    synchronized (DECODE_LOCK) {
      return BitmapFactory.decodeFile(path, bitmapOptions);
    }
  }

  Bitmap decodeResource(Resources resources, int resourceId, PicassoBitmapOptions bitmapOptions) {
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    synchronized (DECODE_LOCK) {
      return BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
    }
  }

  private void cancelExistingRequest(Object target, String path) {
    Request existing = targetsToRequests.remove(target);
    if (existing != null) {
      if (!existing.future.isDone()) {
        existing.future.cancel(true);
      } else if (path == null || !path.equals(existing.path)) {
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

    switch (request.type) {
      case CONTENT:
        Uri path = Uri.parse(request.path);
        exifRotation = Utils.getContentProviderExifRotation(path, context.getContentResolver());
        result = decodeContentStream(path, options);
        request.loadedFrom = Request.LoadedFrom.DISK;
        break;
      case RESOURCE:
        Resources resources = context.getResources();
        result = decodeResource(resources, request.resourceId, options);
        request.loadedFrom = Request.LoadedFrom.DISK;
        break;
      case FILE:
        exifRotation = Utils.getFileExifRotation(request.path);
        result = decodeFile(request.path, options);
        request.loadedFrom = Request.LoadedFrom.DISK;
        break;
      case STREAM:
        Response response = null;
        try {
          response = loader.load(request.path, request.retryCount == 0);
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
        break;
      default:
        throw new AssertionError("Unknown request type: " + request.type);
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

  /** The global default {@link Picasso} instance. */
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

      return new Picasso(context, loader, service, memoryCache, stats);
    }
  }
}
