package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.squareup.picasso.RequestMetrics.LOADED_FROM_DISK;
import static com.squareup.picasso.RequestMetrics.LOADED_FROM_MEM;
import static com.squareup.picasso.RequestMetrics.LOADED_FROM_NETWORK;

public class Picasso {
  private static final int RETRY_DELAY = 500;
  private static final int REQUEST_COMPLETE = 1;
  private static final int REQUEST_RETRY = 2;

  private static final Handler HANDLER = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      Request request = (Request) msg.obj;
      if (request.future.isCancelled()) {
        return;
      }

      switch (msg.what) {
        case REQUEST_COMPLETE:
          request.complete();
          break;

        case REQUEST_RETRY:
          request.picasso.retry(request);
          break;

        default:
          throw new IllegalArgumentException(
              String.format("Unknown handler message received! %d", msg.what));
      }
    }
  };

  private static Picasso singleton = null;

  final Loader loader;
  final ExecutorService service;
  final Cache memoryCache;
  final Map<Object, Request> targetsToRequests;
  final boolean debugging;

  private Picasso(Loader loader, ExecutorService service, Cache memoryCache, boolean debugging) {
    this.loader = loader;
    this.service = service;
    this.memoryCache = memoryCache;
    this.debugging = debugging;
    this.targetsToRequests = new WeakHashMap<Object, Request>();
  }

  public Request.Builder load(String path) {
    return new Request.Builder(this, path);
  }

  void submit(Request request) {
    Object target = request.getTarget();
    if (target == null) return;

    Request existing = targetsToRequests.remove(target);
    if (existing != null) {
      existing.future.cancel(true);
    }

    targetsToRequests.put(target, request);
    request.future = service.submit(request);
  }

  Bitmap run(Request request) {
    Bitmap bitmap = loadFromCaches(request);
    if (bitmap == null) {
      bitmap = loadFromStream(request);
    }
    return bitmap;
  }

  Bitmap quickMemoryCacheCheck(String path) {
    Bitmap cached = null;
    if (memoryCache != null) {
      cached = memoryCache.get(path);
    }
    return cached;
  }

  void retry(Request request) {
    if (request.retryCount > 0) {
      request.retryCount--;
      request.future = service.submit(request);
    } else {
      request.error();
    }
  }

  private Bitmap loadFromCaches(Request request) {
    String path = request.path;
    Bitmap cached = null;
    int loadedFrom = 0;

    if (memoryCache != null) {
      cached = memoryCache.get(path);
      if (debugging && cached != null) {
        request.metrics.loadedFrom = LOADED_FROM_MEM;
        request.result = cached;
        HANDLER.sendMessage(HANDLER.obtainMessage(REQUEST_COMPLETE, request));
      }
    }

    if (cached != null) {
      if (debugging) {
        request.metrics.loadedFrom = loadedFrom;
      }
    }

    return cached;
  }

  private Bitmap loadFromStream(Request request) {
    String path = request.path;
    Bitmap result = null;
    InputStream stream = null;
    try {

      Loader.Response response = loader.load(path, request.retryCount == 0);
      if (response == null) {
        return null;
      }

      stream = response.stream;
      result = BitmapFactory.decodeStream(stream, null, request.bitmapOptions);
      result = transformResult(request, result);

      if (debugging) {
        request.metrics.loadedFrom = response.cached ? LOADED_FROM_DISK : LOADED_FROM_NETWORK;
      }

      if (result != null && memoryCache != null) {
        memoryCache.set(request.key, result);
      }

      request.result = result;
      HANDLER.sendMessage(HANDLER.obtainMessage(REQUEST_COMPLETE, request));
    } catch (Exception e) {
      HANDLER.sendMessageDelayed(HANDLER.obtainMessage(REQUEST_RETRY, request), RETRY_DELAY);
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignored) {
        }
      }
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
      this.loader = loader;
      return this;
    }

    public Builder executor(ExecutorService executorService) {
      this.service = executorService;
      return this;
    }

    public Builder memoryCache(Cache memoryCache) {
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
