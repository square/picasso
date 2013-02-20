package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class Picasso {
  private static final String TAG = "Picasso";
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
  final MemoryCache memoryCache;
  final DiskCache diskCache;
  final Map<Object, Request> targetsToRequests;
  final boolean debugging;

  private Picasso(Loader loader, ExecutorService service, MemoryCache memoryCache,
      DiskCache diskCache, boolean debugging) {
    this.loader = loader;
    this.service = service;
    this.memoryCache = memoryCache;
    this.diskCache = diskCache;
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

    // First check memory cache.
    if (memoryCache != null) {
      cached = memoryCache.get(path);
      if (debugging && cached != null) {
        loadedFrom = RequestMetrics.LOADED_FROM_MEM;
      }
    }

    // Then try disk cache.
    if (cached == null && diskCache != null) {
      try {
        cached = diskCache.get(path);
      } catch (IOException e) {
        if (debugging) {
          Log.e(TAG, String.format("Failed to load image from disk cache!\n%s", request), e);
        }
      }

      // If the disk cache has the bitmap, add it to our memory cache.
      if (cached != null && memoryCache != null) {
        memoryCache.set(path, cached);
        if (debugging) {
          loadedFrom = RequestMetrics.LOADED_FROM_DISK;
        }
      }
    }

    // Finally, if we found a cached image, set it as the result and finish.
    if (cached != null) {
      if (debugging) {
        request.metrics.loadedFrom = loadedFrom;
      }
      request.result = cached;
      HANDLER.sendMessage(HANDLER.obtainMessage(REQUEST_COMPLETE, request));
    }

    return cached;
  }

  private Bitmap loadFromStream(Request request) {
    String path = request.path;
    Bitmap result = null;
    InputStream stream = null;
    try {
      stream = loader.load(path);
      result = BitmapFactory.decodeStream(stream, null, request.bitmapOptions);
      result = transformResult(request, result);

      if (debugging) {
        request.metrics.loadedFrom = RequestMetrics.LOADED_FROM_NETWORK;
      }

      request.result = result;
      HANDLER.sendMessage(HANDLER.obtainMessage(REQUEST_COMPLETE, request));

      if (result != null) {
        saveToCaches(path, result);
      }
    } catch (IOException e) {
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

  private void saveToCaches(String path, Bitmap result) throws IOException {
    if (memoryCache != null) {
      memoryCache.set(path, result);
    }

    if (diskCache != null) {
      diskCache.set(path, result);
    }
  }

  public static Picasso with(Context context) {
    if (singleton == null) {
      DiskCache diskCache = null;
      try {
        diskCache = new LruDiskCache(context);
      } catch (IOException ignored) {
      }

      singleton = new Builder().loader(new ApacheHttpLoader())
          .executor(Executors.newFixedThreadPool(3, new PicassoThreadFactory()))
          .memoryCache(new LruMemoryCache(context))
          .diskCache(diskCache)
          .debug()
          .build();
    }
    return singleton;
  }

  public static class Builder {

    private Loader loader;
    private ExecutorService service;
    private MemoryCache memoryCache;
    private DiskCache diskCache;
    private boolean debugging;

    public Builder loader(Loader loader) {
      this.loader = loader;
      return this;
    }

    public Builder executor(ExecutorService executorService) {
      this.service = executorService;
      return this;
    }

    public Builder memoryCache(MemoryCache memoryCache) {
      this.memoryCache = memoryCache;
      return this;
    }

    public Builder diskCache(DiskCache diskCache) {
      this.diskCache = diskCache;
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
      return new Picasso(loader, service, memoryCache, diskCache, debugging);
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
