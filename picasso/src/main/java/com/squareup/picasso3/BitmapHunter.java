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

import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.squareup.picasso3.RequestHandler.Result;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okio.Buffer;

import static com.squareup.picasso3.MemoryPolicy.shouldReadFromMemoryCache;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso3.Picasso.Priority;
import static com.squareup.picasso3.Picasso.Priority.LOW;
import static com.squareup.picasso3.Utils.OWNER_HUNTER;
import static com.squareup.picasso3.Utils.VERB_DECODED;
import static com.squareup.picasso3.Utils.VERB_EXECUTING;
import static com.squareup.picasso3.Utils.VERB_JOINED;
import static com.squareup.picasso3.Utils.VERB_REMOVED;
import static com.squareup.picasso3.Utils.VERB_TRANSFORMED;
import static com.squareup.picasso3.Utils.getLogIdsForHunter;
import static com.squareup.picasso3.Utils.log;

class BitmapHunter implements Runnable {
  private static final ThreadLocal<StringBuilder> NAME_BUILDER = new ThreadLocal<StringBuilder>() {
    @Override protected StringBuilder initialValue() {
      return new StringBuilder(Utils.THREAD_PREFIX);
    }
  };

  private static final AtomicInteger SEQUENCE_GENERATOR = new AtomicInteger();

  private static final RequestHandler ERRORING_HANDLER = new RequestHandler() {
    @Override public boolean canHandleRequest(@NonNull Request data) {
      return true;
    }

    @Override public void load(@NonNull Picasso picasso, @NonNull Request request,
        @NonNull Callback callback) {
      callback.onError(new IllegalStateException("Unrecognized type of request: " + request));
    }
  };

  final int sequence;
  final Picasso picasso;
  final Dispatcher dispatcher;
  final PlatformLruCache cache;
  final Stats stats;
  final String key;
  Request data;
  final RequestHandler requestHandler;

  @Nullable Action action;
  @Nullable List<Action> actions;
  @Nullable Result result;
  @Nullable Future<?> future;
  @Nullable Exception exception;
  int retryCount;
  Priority priority;

  BitmapHunter(Picasso picasso, Dispatcher dispatcher, PlatformLruCache cache,
      Stats stats, Action action, RequestHandler requestHandler) {
    this.sequence = SEQUENCE_GENERATOR.incrementAndGet();
    this.picasso = picasso;
    this.dispatcher = dispatcher;
    this.cache = cache;
    this.stats = stats;
    this.action = action;
    this.key = action.request.key;
    this.data = action.request;
    this.priority = action.request.priority;
    this.requestHandler = requestHandler;
    this.retryCount = requestHandler.getRetryCount();
  }

  @Override public void run() {
    try {
      updateThreadName(data);

      if (picasso.loggingEnabled) {
        log(OWNER_HUNTER, VERB_EXECUTING, getLogIdsForHunter(this));
      }

      result = hunt();

      if (result.getBitmap() == null && result.getDrawable() == null) {
        dispatcher.dispatchFailed(this);
      } else {
        dispatcher.dispatchComplete(this);
      }
    } catch (NetworkRequestHandler.ResponseException e) {
      if (!NetworkPolicy.isOfflineOnly(e.networkPolicy) || e.code != 504) {
        exception = e;
      }
      dispatcher.dispatchFailed(this);
    } catch (IOException e) {
      exception = e;
      dispatcher.dispatchRetry(this);
    } catch (OutOfMemoryError e) {
      Buffer buffer = new Buffer();
      try {
        stats.createSnapshot().dump(buffer);
      } catch (IOException ioe) {
        throw new AssertionError(ioe);
      }
      exception = new RuntimeException(buffer.readUtf8(), e);
      dispatcher.dispatchFailed(this);
    } catch (Exception e) {
      exception = e;
      dispatcher.dispatchFailed(this);
    } finally {
      Thread.currentThread().setName(Utils.THREAD_IDLE_NAME);
    }
  }

  Result hunt() throws IOException {
    if (shouldReadFromMemoryCache(data.memoryPolicy)) {
      Bitmap bitmap = cache.get(key);
      if (bitmap != null) {
        stats.dispatchCacheHit();
        if (picasso.loggingEnabled) {
          log(OWNER_HUNTER, VERB_DECODED, data.logId(), "from cache");
        }
        return new Result(bitmap, MEMORY);
      }
    }

    if (retryCount == 0) {
      data = data.newBuilder().networkPolicy(NetworkPolicy.OFFLINE).build();
    }

    final AtomicReference<Result> resultReference = new AtomicReference<>();
    final AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);
    try {
      requestHandler.load(picasso, data, new RequestHandler.Callback() {
        @Override public void onSuccess(@Nullable Result result) {
          resultReference.set(result);
          latch.countDown();
        }

        @Override public void onError(@NonNull Throwable t) {
          exceptionReference.set(t);
          latch.countDown();
        }
      });

      latch.await();
    } catch (InterruptedException ie) {
      InterruptedIOException interruptedIoException = new InterruptedIOException();
      interruptedIoException.initCause(ie);
      throw interruptedIoException;
    }

    Throwable throwable = exceptionReference.get();
    if (throwable != null) {
      if (throwable instanceof IOException) {
        throw (IOException) throwable;
      }
      if (throwable instanceof Error) {
        throw (Error) throwable;
      }
      if (throwable instanceof RuntimeException) {
        throw (RuntimeException) throwable;
      }
      throw new RuntimeException(throwable);
    }

    Result result = resultReference.get();
    if (result == null) {
      throw new AssertionError("Request handler neither returned a result nor an exception.");
    }

    Bitmap bitmap = result.getBitmap();
    if (bitmap != null) {
      if (picasso.loggingEnabled) {
        log(OWNER_HUNTER, VERB_DECODED, data.logId());
      }
      stats.dispatchBitmapDecoded(bitmap);

      List<Transformation> transformations = new ArrayList<>(data.transformations.size() + 1);
      if (data.needsMatrixTransform() || result.getExifRotation() != 0) {
        transformations.add(new MatrixTransformation(data));
      }
      transformations.addAll(data.transformations);

      result = applyTransformations(picasso, data, transformations, result);
      bitmap = result.getBitmap();
      if (bitmap != null) {
        stats.dispatchBitmapTransformed(bitmap);
      }
    }

    return result;
  }

  void attach(Action action) {
    boolean loggingEnabled = picasso.loggingEnabled;
    Request request = action.request;

    if (this.action == null) {
      this.action = action;
      if (loggingEnabled) {
        if (actions == null || actions.isEmpty()) {
          log(OWNER_HUNTER, VERB_JOINED, request.logId(), "to empty hunter");
        } else {
          log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
        }
      }
      return;
    }

    if (actions == null) {
      actions = new ArrayList<>(3);
    }

    actions.add(action);

    if (loggingEnabled) {
      log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
    }

    Priority actionPriority = action.request.priority;
    if (actionPriority.ordinal() > priority.ordinal()) {
      priority = actionPriority;
    }
  }

  void detach(Action action) {
    boolean detached = false;
    if (this.action == action) {
      this.action = null;
      detached = true;
    } else if (actions != null) {
      detached = actions.remove(action);
    }

    // The action being detached had the highest priority. Update this
    // hunter's priority with the remaining actions.
    if (detached && action.request.priority == priority) {
      priority = computeNewPriority();
    }

    if (picasso.loggingEnabled) {
      log(OWNER_HUNTER, VERB_REMOVED, action.request.logId(), getLogIdsForHunter(this, "from "));
    }
  }

  private Priority computeNewPriority() {
    Priority newPriority = LOW;

    final boolean hasMultiple = actions != null && !actions.isEmpty();
    final boolean hasAny = action != null || hasMultiple;

    // Hunter has no requests, low priority.
    if (!hasAny) {
      return newPriority;
    }

    if (action != null) {
      newPriority = action.request.priority;
    }

    if (actions != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, n = actions.size(); i < n; i++) {
        Priority actionPriority = actions.get(i).request.priority;
        if (actionPriority.ordinal() > newPriority.ordinal()) {
          newPriority = actionPriority;
        }
      }
    }

    return newPriority;
  }

  boolean cancel() {
    return action == null
        && (actions == null || actions.isEmpty())
        && future != null
        && future.cancel(false);
  }

  boolean isCancelled() {
    return future != null && future.isCancelled();
  }

  boolean shouldRetry(boolean airplaneMode, @Nullable NetworkInfo info) {
    boolean hasRetries = retryCount > 0;
    if (!hasRetries) {
      return false;
    }
    retryCount--;
    return requestHandler.shouldRetry(airplaneMode, info);
  }

  boolean supportsReplay() {
    return requestHandler.supportsReplay();
  }

  @Nullable Result getResult() {
    return result;
  }

  String getKey() {
    return key;
  }

  Request getData() {
    return data;
  }

  @Nullable Action getAction() {
    return action;
  }

  Picasso getPicasso() {
    return picasso;
  }

  @Nullable List<Action> getActions() {
    return actions;
  }

  @Nullable Exception getException() {
    return exception;
  }

  Priority getPriority() {
    return priority;
  }

  static void updateThreadName(Request data) {
    String name = data.getName();

    StringBuilder builder = NAME_BUILDER.get();
    builder.ensureCapacity(Utils.THREAD_PREFIX.length() + name.length());
    builder.replace(Utils.THREAD_PREFIX.length(), builder.length(), name);

    Thread.currentThread().setName(builder.toString());
  }

  static BitmapHunter forRequest(Picasso picasso, Dispatcher dispatcher,
      PlatformLruCache cache, Stats stats, Action action) {
    Request request = action.request;
    List<RequestHandler> requestHandlers = picasso.getRequestHandlers();

    // Index-based loop to avoid allocating an iterator.
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, count = requestHandlers.size(); i < count; i++) {
      RequestHandler requestHandler = requestHandlers.get(i);
      if (requestHandler.canHandleRequest(request)) {
        return new BitmapHunter(picasso, dispatcher, cache, stats, action, requestHandler);
      }
    }

    return new BitmapHunter(picasso, dispatcher, cache, stats, action, ERRORING_HANDLER);
  }

  @SuppressWarnings("NullAway")
  static Result applyTransformations(Picasso picasso, Request data,
      List<Transformation> transformations, Result result) {
    for (int i = 0, count = transformations.size(); i < count; i++) {
      final Transformation transformation = transformations.get(i);
      Result newResult;
      try {
        newResult = transformation.transform(result);
        if (picasso.loggingEnabled) {
          log(OWNER_HUNTER, VERB_TRANSFORMED, data.logId(), "from transformations");
        }
      } catch (final RuntimeException e) {
        Picasso.HANDLER.post(new Runnable() {
          @Override public void run() {
            throw new RuntimeException(
                "Transformation " + transformation.key() + " crashed with exception.", e);
          }
        });
        return null;
      }

      if (newResult == null) {
        final StringBuilder builder = new StringBuilder() //
            .append("Transformation ")
            .append(transformation.key())
            .append(" returned null after ")
            .append(i)
            .append(" previous transformation(s).\n\nTransformation list:\n");
        for (Transformation t : transformations) {
          builder.append(t.key()).append('\n');
        }
        Picasso.HANDLER.post(new Runnable() {
          @Override public void run() {
            throw new NullPointerException(builder.toString());
          }
        });
        return null;
      }

      Bitmap bitmap = result.getBitmap();
      if (bitmap == null) return result;

      if (newResult == result && bitmap.isRecycled()) {
        Picasso.HANDLER.post(new Runnable() {
          @Override public void run() {
            throw new IllegalStateException("Transformation "
                + transformation.key()
                + " returned input Bitmap but recycled it.");
          }
        });
        return null;
      }

      // If the transformation returned a new bitmap ensure they recycled the original.
      if (newResult != result
          && newResult.getBitmap() != bitmap
          && !bitmap.isRecycled()) {
        Picasso.HANDLER.post(new Runnable() {
          @Override public void run() {
            throw new IllegalStateException("Transformation "
                + transformation.key()
                + " mutated input Bitmap but failed to recycle the original.");
          }
        });
        return null;
      }

      result = newResult;
    }

    return result;
  }
}
