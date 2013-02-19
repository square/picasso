package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Picasso {
  private static final int RETRY_DELAY = 500;
  private static final int PROCESS_RESULT = 1;
  private static final int RETRY_REQUEST = 2;

  private static Picasso singleton = null;
  private static final Handler handler = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      Request request;
      switch (msg.what) {
        case PROCESS_RESULT:
          request = (Request) msg.obj;
          if (request.getFuture().isCancelled() || request.getResult() == null) return;
          request.picasso.complete(request);
          break;
        case RETRY_REQUEST:
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
      // TODO LOG YOU FUCKED UP.
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

  void run(Request request) {
    if (loadFromCaches(request)) return;
    loadFromStream(request);
  }

  void complete(Request request) {
    Bitmap result = request.getResult();
    if (result == null) throw new AssertionError("WTF?");

    ImageView imageView = request.target.get();
    if (imageView != null) {
      if (debugging) {
        int color = RequestMetrics.getColorCodeForCacheHit(request.getMetrics().loadedFrom);
        imageView.setBackgroundColor(color);
      }
      imageView.setImageBitmap(result);
    }
  }

  private boolean loadFromCaches(Request request) {
    String path = request.getPath();
    Bitmap cached = null;
    int loadedFrom = 0;

    try {
      // First check memory cache.
      if (memoryCache != null) {
        cached = memoryCache.get(path);
        if (debugging && cached != null) {
          loadedFrom = RequestMetrics.LOADED_FROM_MEM;
        }
      }

      // Then try disk cache.
      if (cached == null && diskCache != null) {
        cached = diskCache.get(path);

        // If the disk cache has the bitmap, add it to our memory cache.
        if (cached != null && memoryCache != null) {
          if (debugging) {
            loadedFrom = RequestMetrics.LOADED_FROM_DISK;
          }
          memoryCache.set(path, cached);
        }
      }

      // Finally, if we found a cached image, set it as the result and finish.
      if (cached != null) {
        if (debugging) {
          request.getMetrics().loadedFrom = loadedFrom;
        }
        request.setResult(cached);
        handler.sendMessage(handler.obtainMessage(PROCESS_RESULT, request));
      }
    } catch (IOException e) {
      // TODO HANDLE THIS SHIT.
      e.printStackTrace();
    }

    return cached != null;
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

  private void loadFromStream(Request request) {
    String path = request.getPath();
    InputStream stream = null;
    try {
      stream = loader.load(path);
      Bitmap result = BitmapFactory.decodeStream(stream, null, request.bitmapOptions);
      result = transformResult(request, result);

      if (debugging) {
        request.getMetrics().loadedFrom = RequestMetrics.LOADED_FROM_NETWORK;
      }

      request.setResult(result);
      handler.sendMessage(handler.obtainMessage(PROCESS_RESULT, request));

      if (result != null) {
        saveToCaches(path, result);
      }
    } catch (IOException e) {
      handler.sendMessageDelayed(handler.obtainMessage(RETRY_REQUEST, request), RETRY_DELAY);
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignored) {
        }
      }
    }
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
}
