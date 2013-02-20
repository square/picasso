package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Picasso {
  private static final String TAG = "Picasso";

  private static final int RETRY_DELAY = 500;
  private static final int REQUEST_COMPLETE = 1;
  private static final int REQUEST_RETRY = 2;

  private static Picasso singleton = null;
  private static final Handler handler = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      Request request;
      switch (msg.what) {
        case REQUEST_COMPLETE:
          request = (Request) msg.obj;
          if (request.getFuture().isCancelled() || request.getResult() == null) return;
          request.picasso.complete(request);
          break;
        case REQUEST_RETRY:
          request = (Request) msg.obj;
          if (request.getFuture().isCancelled() || request.retryCount == 0) return;

          request.retryCount--;
          request.setFuture(request.picasso.service.submit(request));
          break;
      }
    }
  };

  final boolean debugging;
  final Loader loader;
  final ExecutorService service;
  final Cache memoryCache;
  final Cache diskCache;
  final Map<ImageView, Request> targetsToRequests = new WeakHashMap<ImageView, Request>();

  public Picasso(Context context, boolean debugging) {
    this.loader = new ApacheHttpLoader();
    this.service = Executors.newSingleThreadExecutor();

    this.memoryCache = new LruMemoryCache(context);
    Cache diskCache = null;
    try {
      diskCache = new LruDiskCache(context);
    } catch (IOException e) {
      Log.w(TAG, "Unable to create disk cache!", e);
    }
    this.diskCache = diskCache;
    this.debugging = debugging;
  }

  public Request.Builder load(String path) {
    return new Request.Builder(this, path);
  }

  void submit(Request request) {
    ImageView target = request.getTarget();
    if (target == null) return;

    Request existing = targetsToRequests.remove(target);
    if (existing != null) {
      existing.getFuture().cancel(true);
    }

    targetsToRequests.put(target, request);
    request.setFuture(service.submit(request));
  }

  Bitmap run(Request request) {
    Bitmap bitmap = loadFromCaches(request);
    if (bitmap == null) {
      bitmap = loadFromStream(request);
    }
    return bitmap;
  }

  void complete(Request request) {
    Bitmap result = request.getResult();
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", request));
    }

    ImageView imageView = request.target.get();
    if (imageView != null) {
      if (debugging) {
        int color = RequestMetrics.getColorCodeForCacheHit(request.getMetrics().loadedFrom);
        imageView.setBackgroundColor(color);
      }
      imageView.setImageBitmap(result);
    }
  }

  boolean quickCacheCheck(ImageView target, String path, RequestMetrics metrics) {
    Bitmap cached = null;
    if (memoryCache != null) {
      try {
        cached = memoryCache.get(path);
      } catch (IOException e) {
        return false;
      }

      if (cached != null) {
        target.setImageBitmap(cached);
        if (debugging) {
          metrics.executedTime = System.nanoTime();
          metrics.loadedFrom = RequestMetrics.LOADED_FROM_MEM;
          target.setBackgroundColor(RequestMetrics.getColorCodeForCacheHit(metrics.loadedFrom));
        }
      }
    }
    return cached != null;
  }

  private Bitmap loadFromCaches(Request request) {
    String path = request.getPath();
    Bitmap cached = null;
    int loadedFrom = 0;

    // First check memory cache.
    if (memoryCache != null) {
      try {
        cached = memoryCache.get(path);
        if (debugging && cached != null) {
          loadedFrom = RequestMetrics.LOADED_FROM_MEM;
        }
      } catch (IOException e) {
        Log.e(TAG, String.format("Failed to load image from memory!\n%s", request), e);
      }
    }

    // Then try disk cache.
    if (cached == null && diskCache != null) {
      try {
        cached = diskCache.get(path);
      } catch (IOException e) {
        Log.e(TAG, String.format("Failed to load image from disk cache!\n%s", request), e);
      }

      // If the disk cache has the bitmap, add it to our memory cache.
      if (cached != null && memoryCache != null) {
        try {
          memoryCache.set(path, cached);
        } catch (IOException e) {
          Log.e(TAG, String.format("Failed to set image into memory cache!\n%s", request), e);
        }
        if (debugging) {
          loadedFrom = RequestMetrics.LOADED_FROM_DISK;
        }
      }
    }

    // Finally, if we found a cached image, set it as the result and finish.
    if (cached != null) {
      if (debugging) {
        request.getMetrics().loadedFrom = loadedFrom;
      }
      request.setResult(cached);
      handler.sendMessage(handler.obtainMessage(REQUEST_COMPLETE, request));
    }

    return cached;
  }

  private Bitmap loadFromStream(Request request) {
    String path = request.getPath();
    Bitmap result = null;
    InputStream stream = null;
    try {
      stream = loader.load(path);
      result = BitmapFactory.decodeStream(stream, null, request.bitmapOptions);
      result = transformResult(request, result);

      if (debugging) {
        request.getMetrics().loadedFrom = RequestMetrics.LOADED_FROM_NETWORK;
      }

      request.setResult(result);
      handler.sendMessage(handler.obtainMessage(REQUEST_COMPLETE, request));

      if (result != null) {
        saveToCaches(path, result);
      }
    } catch (IOException e) {
      handler.sendMessageDelayed(handler.obtainMessage(REQUEST_RETRY, request), RETRY_DELAY);
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
      singleton = new Picasso(context.getApplicationContext(), true);
    }
    return singleton;
  }

  static void checkNotMain() {
    if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
      throw new AssertionError("Method call should not happen from the main thread.");
    }
  }
}
