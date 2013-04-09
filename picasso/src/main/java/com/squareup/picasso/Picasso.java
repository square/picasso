package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import static android.graphics.BitmapFactory.Options;
import static com.squareup.picasso.Loader.Response;
import static com.squareup.picasso.Request.TYPE_FILE;
import static com.squareup.picasso.Request.TYPE_STREAM;
import static com.squareup.picasso.RequestMetrics.LOADED_FROM_DISK;
import static com.squareup.picasso.RequestMetrics.LOADED_FROM_MEM;
import static com.squareup.picasso.RequestMetrics.LOADED_FROM_NETWORK;

public class Picasso {
  private static final int RETRY_DELAY = 500;
  private static final int REQUEST_COMPLETE = 1;
  private static final int REQUEST_RETRY = 2;
  private static final int REQUEST_DECODE_FAILED = 3;

  // TODO This should be static.
  private final Handler handler = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      Request request = (Request) msg.obj;
      if (request.future.isCancelled()) {
        return;
      }

      switch (msg.what) {
        case REQUEST_COMPLETE:
          targetsToRequests.remove(request.getTarget());
          request.complete();
          break;

        case REQUEST_RETRY:
          request.picasso.retry(request);
          break;

        case REQUEST_DECODE_FAILED:
          targetsToRequests.remove(request.getTarget());
          request.error();
          break;

        default:
          throw new AssertionError(String.format("Unknown handler message received! %d", msg.what));
      }
    }
  };

  static Picasso singleton = null;

  final Loader loader;
  final ExecutorService service;
  final Cache memoryCache;
  final Map<Object, Request> targetsToRequests;

  boolean debugging;

  private Picasso(Loader loader, ExecutorService service, Cache memoryCache, boolean debugging) {
    this.loader = loader;
    this.service = service;
    this.memoryCache = memoryCache;
    this.debugging = debugging;
    this.targetsToRequests = new WeakHashMap<Object, Request>();
  }

  public Request.Builder load(String path) {
    return new Request.Builder(this, path, TYPE_STREAM);
  }

  public Request.Builder load(File file) {
    return new Request.Builder(this, file.getPath(), TYPE_FILE);
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
        cached = Utils.applyDebugColorFilter(cached, LOADED_FROM_MEM);
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

  Bitmap decodeStream(InputStream stream, Options bitmapOptions) {
    if (stream == null) return null;
    return BitmapFactory.decodeStream(stream, null, bitmapOptions);
  }

  Bitmap decodeFile(String path, Options bitmapOptions) {
    return BitmapFactory.decodeFile(path, bitmapOptions);
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
            request.metrics.loadedFrom = LOADED_FROM_MEM;
          }
          cached = Utils.applyDebugColorFilter(cached, LOADED_FROM_MEM);
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
        case TYPE_STREAM:
          Response response = null;
          try {
            response = loader.load(request.path, request.retryCount == 0);
            if (response == null) {
              return null;
            }
          } finally {
            if (response != null && response.stream != null) {
              try {
                response.stream.close();
              } catch (IOException ignored) {
              }
            }
          }

          result = decodeStream(response.stream, request.bitmapOptions);
          fromDisk = response.cached;
          break;
        case TYPE_FILE:
          result = decodeFile(request.path, request.bitmapOptions);
          fromDisk = true;
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
          request.metrics.loadedFrom = fromDisk ? LOADED_FROM_DISK : LOADED_FROM_NETWORK;
          // For color coded debugging, apply color filter after the bitmap is added to the cache.
          if (result != null) {
            result = Utils.applyDebugColorFilter(result, request.metrics.loadedFrom);
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

  private Bitmap transformResult(Request request, Bitmap result) {
    List<Transformation> transformations = request.transformations;
    if (!transformations.isEmpty()) {
      if (transformations.size() == 1) {
        result = transformations.get(0).transform(result);
      } else {
        for (Transformation transformation : transformations) {
          result = transformation.transform(result);
        }
      }
    }
    return result;
  }

  public static Picasso with(Context context) {
    if (singleton == null) {
      singleton = new Builder().loader(new DefaultHttpLoader(context))
          .executor(Executors.newFixedThreadPool(3, new PicassoThreadFactory()))
          .memoryCache(new LruMemoryCache(context))
          .debug()
          .build();
    }
    return singleton;
  }

  public static class Builder {

    private Loader loader;
    private ExecutorService service;
    private Cache memoryCache;
    private boolean debugging;

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
      if (loader == null || service == null) {
        throw new IllegalStateException("Must provide a Loader and an ExecutorService.");
      }
      return new Picasso(loader, service, memoryCache, debugging);
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
