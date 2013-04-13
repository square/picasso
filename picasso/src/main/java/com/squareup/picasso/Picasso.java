package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.squareup.picasso.Loader.Response;
import static com.squareup.picasso.Request.Type;
import static com.squareup.picasso.RequestMetrics.LoadedFrom;
import static com.squareup.picasso.Utils.calculateInSampleSize;

public class Picasso {
  private static final int RETRY_DELAY = 500;
  private static final int REQUEST_COMPLETE = 1;
  private static final int REQUEST_RETRY = 2;
  private static final int REQUEST_DECODE_FAILED = 3;

  private static final String FILE_URL_SCHEME = "file:";
  private static final String CONTENT_URL_PREFIX = "content:";

  // TODO This should be static.
  private final Handler handler = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      Request request = (Request) msg.obj;
      if (request.future.isCancelled()) {
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
          throw new AssertionError(String.format("Unknown handler message received! %d", msg.what));
      }
    }
  };

  static Picasso singleton = null;

  final Context context;
  final Loader loader;
  final ExecutorService service;
  final Cache memoryCache;
  final Map<Object, Request> targetsToRequests;

  boolean debugging;

  private Picasso(Context context, Loader loader, ExecutorService service, Cache memoryCache,
      boolean debugging) {
    this.context = context;
    this.loader = loader;
    this.service = service;
    this.memoryCache = memoryCache;
    this.debugging = debugging;
    this.targetsToRequests = new WeakHashMap<Object, Request>();
  }

  public Request.Builder load(String path) {
    if (path != null) {
      if (path.startsWith(FILE_URL_SCHEME)) {
        return new Request.Builder(this, Uri.parse(path).getPath(), 0, Type.FILE);
      }
      if (path.startsWith(CONTENT_URL_PREFIX)) {
        return new Request.Builder(this, path, 0, Type.CONTENT);
      }
    }
    return new Request.Builder(this, path, 0, Type.STREAM);
  }

  public Request.Builder load(File file) {
    if (file == null) {
      throw new IllegalArgumentException("File may not be null.");
    }
    return new Request.Builder(this, file.getPath(), 0, Type.FILE);
  }

  public Request.Builder load(int resourceId) {
    if (resourceId == 0) {
      throw new IllegalArgumentException("Resource ID must not be zero.");
    }
    return new Request.Builder(this, null, resourceId, Type.RESOURCE);
  }

  public boolean isDebugging() {
    return debugging;
  }

  public void setDebugging(boolean enabled) {
    this.debugging = enabled;
  }

  void submit(Request request) {
    Object target = request.getTarget();
    if (target == null) return;

    cancelExistingRequest(target, request.path);

    targetsToRequests.put(target, request);
    request.future = service.submit(request);
  }

  Bitmap run(Request request) {
    Bitmap bitmap = loadFromCache(request);
    if (bitmap == null) {
      bitmap = loadFromType(request);
    }
    return bitmap;
  }

  Bitmap quickMemoryCacheCheck(Object target, String path) {
    Bitmap cached = null;
    if (memoryCache != null) {
      cached = memoryCache.get(path);
      if (debugging && cached != null) {
        cached = Utils.applyDebugSourceIndicator(cached, LoadedFrom.MEM);
      }
    }

    cancelExistingRequest(target, path);

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

  Bitmap decodeFile(String path, PicassoBitmapOptions bitmapOptions) {
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeFile(path, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    return BitmapFactory.decodeFile(path, bitmapOptions);
  }

  Bitmap decodeResource(Resources resources, int resourceId, PicassoBitmapOptions bitmapOptions) {
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    return BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
  }

  private void cancelExistingRequest(Object target, String path) {
    Request existing = targetsToRequests.remove(target);
    if (existing != null) {
      if (!existing.future.isDone()) {
        existing.future.cancel(true);
      } else if (!path.equals(existing.path)) {
        existing.retryCancelled = true;
      }
    }
  }

  private Bitmap loadFromCache(Request request) {
    Bitmap cached = null;

    if (memoryCache != null) {
      cached = memoryCache.get(request.key);
      if (cached != null) {
        if (debugging) {
          if (request.metrics != null) {
            request.metrics.loadedFrom = LoadedFrom.MEM;
          }
          cached = Utils.applyDebugSourceIndicator(cached, LoadedFrom.MEM);
        }
        request.result = cached;
        handler.sendMessage(handler.obtainMessage(REQUEST_COMPLETE, request));
      }
    }
    return cached;
  }

  private Bitmap loadFromType(Request request) {
    Bitmap result = null;
    boolean fromDisk;
    try {
      switch (request.type) {
        case CONTENT:
          Uri path = Uri.parse(request.path);
          result = decodeContentStream(path, request.bitmapOptions);
          fromDisk = true;
          break;
        case RESOURCE:
          Resources resources = context.getResources();
          result = decodeResource(resources, request.resourceId, request.bitmapOptions);
          fromDisk = true;
          break;
        case FILE:
          result = decodeFile(request.path, request.bitmapOptions);
          fromDisk = true;
          break;
        case STREAM:
          Response response = null;
          try {
            response = loader.load(request.path, request.retryCount == 0);
            if (response == null) {
              return null;
            }
            result = decodeStream(response.stream, request.bitmapOptions);
          } finally {
            if (response != null && response.stream != null) {
              try {
                response.stream.close();
              } catch (IOException ignored) {
              }
            }
          }
          fromDisk = response.cached;
          break;
        default:
          throw new AssertionError("Unknown request type. " + request.type);
      }

      if (result == null) {
        handler.sendMessage(handler.obtainMessage(REQUEST_DECODE_FAILED, request));
        return null;
      }

      result = transformResult(request, result);

      if (result != null && memoryCache != null) {
        memoryCache.set(request.key, result);
      }

      if (debugging) {
        if (request.metrics != null) {
          request.metrics.loadedFrom = fromDisk ? LoadedFrom.DISK : LoadedFrom.NETWORK;
          // For color coded debugging, apply color filter after the bitmap is added to the cache.
          if (result != null) {
            result = Utils.applyDebugSourceIndicator(result, request.metrics.loadedFrom);
          }
        }
      }

      request.result = result;
      handler.sendMessage(handler.obtainMessage(REQUEST_COMPLETE, request));
    } catch (IOException e) {
      handler.sendMessageDelayed(handler.obtainMessage(REQUEST_RETRY, request), RETRY_DELAY);
    }

    return result;
  }

  Bitmap transformResult(Request request, Bitmap result) {
    List<Transformation> transformations = request.transformations;
    if (!transformations.isEmpty()) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, count = transformations.size(); i < count; i++) {
        Transformation t = transformations.get(i);
        result = t.transform(result);
        if (result == null) {
          throw new NullPointerException("Transformation "
              + t.key()
              + " returned null when transforming "
              + request.path
              + " after "
              + i
              + " previous transformations. Transformation list: "
              + request.transformationKeys());
        }
      }
    }
    return result;
  }

  public static Picasso with(Context context) {
    if (singleton == null) {
      singleton = new Builder(context).build();
    }
    return singleton;
  }

  @SuppressWarnings("UnusedDeclaration") // Public API.
  public static class Builder {
    private final Context context;
    private Loader loader;
    private ExecutorService service;
    private Cache memoryCache;
    private boolean debugging;

    public Builder(Context context) {
      if (context == null) {
        throw new IllegalArgumentException("Context may not be null.");
      }
      this.context = context.getApplicationContext();
    }

    public Builder loader(Loader loader) {
      if (loader == null) {
        throw new IllegalArgumentException("Loader may not be null.");
      }
      if (this.loader != null) {
        throw new IllegalStateException("Loader already set.");
      }
      this.loader = loader;
      return this;
    }

    public Builder executor(ExecutorService executorService) {
      if (executorService == null) {
        throw new IllegalArgumentException("Executor service may not be null.");
      }
      if (this.service != null) {
        throw new IllegalStateException("Executor service already set.");
      }
      this.service = executorService;
      return this;
    }

    public Builder memoryCache(Cache memoryCache) {
      if (memoryCache == null) {
        throw new IllegalArgumentException("Memory cache may not be null.");
      }
      if (this.memoryCache != null) {
        throw new IllegalStateException("Memory cache already set.");
      }
      this.memoryCache = memoryCache;
      return this;
    }

    public Builder debug() {
      debugging = true;
      return this;
    }

    public Picasso build() {
      if (loader == null) {
        loader = new DefaultHttpLoader(context);
      }
      if (memoryCache == null) {
        memoryCache = new LruCache(context);
      }
      if (service == null) {
        service = Executors.newFixedThreadPool(3, new PicassoThreadFactory());
      }
      return new Picasso(context, loader, service, memoryCache, debugging);
    }
  }

  static class PicassoThreadFactory implements ThreadFactory {
    private static final AtomicInteger id = new AtomicInteger();

    @SuppressWarnings("NullableProblems") public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setName("picasso-" + id.getAndIncrement());
      t.setPriority(Process.THREAD_PRIORITY_BACKGROUND);

      return t;
    }
  }
}
