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

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import com.squareup.picasso3.Picasso.RequestTransformer;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Response;
import okio.Timeout;
import org.mockito.stubbing.Answer;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.graphics.Bitmap.Config.ALPHA_8;
import static android.provider.ContactsContract.Contacts.CONTENT_URI;
import static android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso3.Picasso.Priority;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestUtils {
  static final Answer<Object> TRANSFORM_REQUEST_ANSWER = invocation -> invocation.getArguments()[0];
  static final Uri URI_1 = Uri.parse("http://example.com/1.png");
  static final Uri URI_2 = Uri.parse("http://example.com/2.png");
  static final String STABLE_1 = "stableExampleKey1";
  static final Request SIMPLE_REQUEST = new Request.Builder(URI_1).build();
  static final String URI_KEY_1 = SIMPLE_REQUEST.key;
  static final String URI_KEY_2 = new Request.Builder(URI_2).build().key;
  static final String STABLE_URI_KEY_1 = new Request.Builder(URI_1).stableKey(STABLE_1).build().key;
  static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
  static final String FILE_KEY_1 = new Request.Builder(Uri.fromFile(FILE_1)).build().key;
  static final Uri FILE_1_URL = Uri.parse("file:///" + FILE_1.getPath());
  static final Uri FILE_1_URL_NO_AUTHORITY = Uri.parse("file:/" + FILE_1.getParent());
  static final Uri MEDIA_STORE_CONTENT_1_URL = Uri.parse("content://media/external/images/media/1");
  static final String MEDIA_STORE_CONTENT_KEY_1 =
      new Request.Builder(MEDIA_STORE_CONTENT_1_URL).build().key;
  static final Uri CONTENT_1_URL = Uri.parse("content://zip/zap/zoop.jpg");
  static final String CONTENT_KEY_1 = new Request.Builder(CONTENT_1_URL).build().key;
  static final Uri CONTACT_URI_1 = CONTENT_URI.buildUpon().appendPath("1234").build();
  static final String CONTACT_KEY_1 = new Request.Builder(CONTACT_URI_1).build().key;
  static final Uri CONTACT_PHOTO_URI_1 =
      CONTENT_URI.buildUpon().appendPath("1234").appendPath(CONTENT_DIRECTORY).build();
  static final String CONTACT_PHOTO_KEY_1 = new Request.Builder(CONTACT_PHOTO_URI_1).build().key;
  static final int RESOURCE_ID_1 = 1;
  static final String RESOURCE_ID_KEY_1 = new Request.Builder(RESOURCE_ID_1).build().key;
  static final Uri ASSET_URI_1 = Uri.parse("file:///android_asset/foo/bar.png");
  static final String ASSET_KEY_1 = new Request.Builder(ASSET_URI_1).build().key;
  static final String RESOURCE_PACKAGE = "com.squareup.picasso3";
  static final String RESOURCE_TYPE = "drawable";
  static final String RESOURCE_NAME = "foo";
  static final Uri RESOURCE_ID_URI = new Uri.Builder().scheme(SCHEME_ANDROID_RESOURCE)
      .authority(RESOURCE_PACKAGE)
      .appendPath(Integer.toString(RESOURCE_ID_1))
      .build();
  static final String RESOURCE_ID_URI_KEY = new Request.Builder(RESOURCE_ID_URI).build().key;
  static final Uri RESOURCE_TYPE_URI = new Uri.Builder().scheme(SCHEME_ANDROID_RESOURCE)
      .authority(RESOURCE_PACKAGE)
      .appendPath(RESOURCE_TYPE)
      .appendPath(RESOURCE_NAME)
      .build();
  static final String RESOURCE_TYPE_URI_KEY =
      new Request.Builder(RESOURCE_TYPE_URI).build().key;
  static final Uri CUSTOM_URI = Uri.parse("foo://bar");
  static final String CUSTOM_URI_KEY = new Request.Builder(CUSTOM_URI).build().key;
  static final String BITMAP_RESOURCE_VALUE = "foo.png";
  static final String XML_RESOURCE_VALUE = "foo.xml";
  static final Bitmap.Config DEFAULT_CONFIG = Bitmap.Config.ARGB_8888;
  static final int DEFAULT_CACHE_SIZE = 123;

  static Context mockPackageResourceContext() {
    Context context = mock(Context.class);
    PackageManager pm = mock(PackageManager.class);
    Resources res = mock(Resources.class);

    doReturn(pm).when(context).getPackageManager();
    try {
      doReturn(res).when(pm).getResourcesForApplication(RESOURCE_PACKAGE);
    } catch (PackageManager.NameNotFoundException e) {
      throw new RuntimeException(e);
    }
    doReturn(RESOURCE_ID_1).when(res).getIdentifier(RESOURCE_NAME, RESOURCE_TYPE, RESOURCE_PACKAGE);
    return context;
  }

  static Resources mockResources(final String resValueString) {
    Resources resources = mock(Resources.class);
    doAnswer((Answer<Void>) invocation -> {
      Object[] args = invocation.getArguments();
      ((TypedValue) args[1]).string = resValueString;
      return null;
    }).when(resources).getValue(anyInt(), any(TypedValue.class), anyBoolean());

    return resources;
  }

  static Request mockRequest(Uri uri) {
    return new Request.Builder(uri).build();
  }

  static FakeAction mockAction(String key, Uri uri) {
    return mockAction(key, uri, null, 0, null, null);
  }

  static FakeAction mockAction(String key, Uri uri, Object target) {
    return mockAction(key, uri, target, 0, null, null);
  }

  static FakeAction mockAction(String key, Uri uri, Priority priority) {
    return mockAction(key, uri, null, 0, priority, null);
  }

  static FakeAction mockAction(String key, Uri uri, String tag) {
    return mockAction(key, uri, null, 0, null, tag);
  }

  static FakeAction mockAction(String key, Uri uri, Object target, String tag) {
    return mockAction(key, uri, target, 0, null, tag);
  }

  static FakeAction mockAction(String key, Uri uri, Object target, int resourceId) {
    return mockAction(key, uri, target, resourceId, null, null);
  }

  static FakeAction mockAction(String key, Uri uri, Object target, int resourceId,
      Priority priority, String tag) {
    Request.Builder builder = new Request.Builder(uri, resourceId, DEFAULT_CONFIG).stableKey(key);
    if (priority != null) {
      builder.priority(priority);
    }
    if (tag != null) {
      builder.tag(tag);
    }
    Request request = builder.build();
    return mockAction(request, target);
  }

  static FakeAction mockAction(Request request) {
    return mockAction(request, null);
  }

  static FakeAction mockAction(Request request, final Object target) {
    return new FakeAction(mockPicasso(), request, target);
  }

  static ImageView mockImageViewTarget() {
    return mock(ImageView.class);
  }

  static RemoteViews mockRemoteViews() {
    return mock(RemoteViews.class);
  }

  static Notification mockNotification() {
    return mock(Notification.class);
  }

  static ImageView mockFitImageViewTarget(boolean alive) {
    ViewTreeObserver observer = mock(ViewTreeObserver.class);
    when(observer.isAlive()).thenReturn(alive);
    ImageView mock = mock(ImageView.class);
    when(mock.getWindowToken()).thenReturn(mock(IBinder.class));
    when(mock.getViewTreeObserver()).thenReturn(observer);
    return mock;
  }

  static BitmapTarget mockTarget() {
    return mock(BitmapTarget.class);
  }

  static Callback mockCallback() {
    return mock(Callback.class);
  }

  static DeferredRequestCreator mockDeferredRequestCreator(ImageView target) {
    ViewTreeObserver observer = mock(ViewTreeObserver.class);
    when(target.getViewTreeObserver()).thenReturn(observer);
    return new DeferredRequestCreator(mock(RequestCreator.class), target, null);
  }

  static NetworkInfo mockNetworkInfo() {
    return mockNetworkInfo(false);
  }

  static NetworkInfo mockNetworkInfo(boolean isConnected) {
    NetworkInfo mock = mock(NetworkInfo.class);
    when(mock.isConnected()).thenReturn(isConnected);
    when(mock.isConnectedOrConnecting()).thenReturn(isConnected);
    return mock;
  }

  static BitmapHunter mockHunter(RequestHandler.Result result) {
    return mockHunter(result, null);
  }

  static BitmapHunter mockHunter(RequestHandler.Result result, Action action) {
    return mockHunter(result, action, null);
  }

  static BitmapHunter mockHunter(RequestHandler.Result result, Action action, Exception e) {
    return mockHunter(result, action, e, false);
  }

  static BitmapHunter mockHunter(RequestHandler.Result result, Action action, boolean shouldRetry) {
    return mockHunter(result, action, null, shouldRetry);
  }

  static BitmapHunter mockHunter(RequestHandler.Result result, Action action, boolean shouldRetry,
      boolean supportsReplay) {
    return mockHunter(result, action, null, shouldRetry, supportsReplay);
  }

  static BitmapHunter mockHunter(RequestHandler.Result result, Action action, Exception e,
      boolean shouldRetry) {
    return mockHunter(result, action, e, shouldRetry, false);
  }

  static BitmapHunter mockHunter(RequestHandler.Result result, Action action, Exception e,
      boolean shouldRetry, boolean supportsReplay) {
    return new BitmapHunterTest.TestableBitmapHunter(
        mockPicasso(), mock(Dispatcher.class), new PlatformLruCache(0), action,
        ((RequestHandler.Result.Bitmap) result).getBitmap(), e, shouldRetry, supportsReplay
    );
  }

  static Picasso mockPicasso() {
    // Inject a RequestHandler that can handle any request.
    RequestHandler requestHandler = new RequestHandler() {
      @Override public boolean canHandleRequest(@NonNull Request data) {
        return true;
      }

      @Override public void load(@NonNull Picasso picasso, @NonNull Request request, @NonNull
          Callback callback) {
        Bitmap defaultResult = makeBitmap();
        RequestHandler.Result result = new RequestHandler.Result.Bitmap(defaultResult, MEMORY);
        callback.onSuccess(result);
      }
    };

    return mockPicasso(requestHandler);
  }

  static Picasso mockPicasso(RequestHandler requestHandler) {
    Picasso picasso = mock(Picasso.class);
    when(picasso.getRequestHandlers()).thenReturn(Collections.singletonList(requestHandler));
    return picasso;
  }

  static Bitmap makeBitmap() {
    return makeBitmap(10, 10);
  }

  static Bitmap makeBitmap(int width, int height) {
    return Bitmap.createBitmap(width, height, ALPHA_8);
  }

  static DrawableLoader makeLoaderWithDrawable(final Drawable drawable) {
    return resId -> drawable;
  }

  static class FakeAction extends Action {
    Object target;
    RequestHandler.Result completedResult;
    Exception errorException;

    public FakeAction(@NonNull Picasso picasso, @NonNull Request request, @NonNull Object target) {
      super(picasso, request);
      this.target = target;
    }

    @Override public void complete(@NonNull RequestHandler.Result result) {
      completedResult = result;
    }

    @Override public void error(@NonNull Exception e) {
      errorException = e;
    }

    @NonNull @Override public Object getTarget() {
      return target;
    }
  }

  static final Call.Factory UNUSED_CALL_FACTORY = request -> { throw new AssertionError(); };

  static final RequestHandler NOOP_REQUEST_HANDLER = new RequestHandler() {
    @Override public boolean canHandleRequest(@NonNull Request data) {
      return false;
    }

    @Override public void load(@NonNull Picasso picasso, @NonNull Request request,
        @NonNull Callback callback) {
    }
  };

  static final RequestTransformer NOOP_TRANSFORMER = request -> new Request.Builder(0).build();

  static final Picasso.Listener NOOP_LISTENER = (picasso, uri, exception) -> { };

  static final List<RequestTransformer> NO_TRANSFORMERS = Collections.emptyList();
  static final List<RequestHandler> NO_HANDLERS = Collections.emptyList();
  static final List<EventListener> NO_EVENT_LISTENERS = Collections.emptyList();

  static Picasso defaultPicasso(Context context, boolean hasRequestHandlers,
      boolean hasTransformers) {
    Picasso.Builder builder = new Picasso.Builder(context);

    if (hasRequestHandlers) {
      builder.addRequestHandler(NOOP_REQUEST_HANDLER);
    }
    if (hasTransformers) {
      builder.addRequestTransformer(NOOP_TRANSFORMER);
    }
    return builder
        .callFactory(UNUSED_CALL_FACTORY)
        .defaultBitmapConfig(DEFAULT_CONFIG)
        .executor(new PicassoExecutorService())
        .indicatorsEnabled(true)
        .listener(NOOP_LISTENER)
        .loggingEnabled(true)
        .withCacheSize(DEFAULT_CACHE_SIZE)
        .build();
  }

  static final class EventRecorder implements EventListener {
    int maxCacheSize = 0;
    int cacheSize = 0;
    int cacheHits = 0;
    int cacheMisses = 0;
    long downloadSize = 0;
    Bitmap decodedBitmap = null;
    Bitmap transformedBitmap = null;
    boolean closed = false;

    @Override public void cacheMaxSize(int maxSize) {
      maxCacheSize = maxSize;
    }

    @Override public void cacheSize(int size) {
      cacheSize = size;
    }

    @Override public void cacheHit() {
      cacheHits++;
    }

    @Override public void cacheMiss() {
      cacheMisses++;
    }

    @Override public void downloadFinished(long size) {
      downloadSize = size;
    }

    @Override public void bitmapDecoded(@NonNull Bitmap bitmap) {
      decodedBitmap = bitmap;
    }

    @Override public void bitmapTransformed(@NonNull Bitmap bitmap) {
      transformedBitmap = bitmap;
    }

    @Override public void close() {
      closed = true;
    }
  }

  static final class PremadeCall implements Call {
    private final okhttp3.Request request;
    private final Response response;

    PremadeCall(okhttp3.Request request, Response response) {
      this.request = request;
      this.response = response;
    }

    @NonNull @Override public okhttp3.Request request() {
      return request;
    }

    @NonNull @Override public Response execute() {
      return response;
    }

    @Override public void enqueue(@NonNull okhttp3.Callback responseCallback) {
      try {
        responseCallback.onResponse(this, response);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    @Override public void cancel() {
      throw new AssertionError();
    }

    @Override public boolean isExecuted() {
      throw new AssertionError();
    }

    @Override public boolean isCanceled() {
      throw new AssertionError();
    }

    @NonNull @Override public Call clone() {
      throw new AssertionError();
    }

    @NonNull @Override public Timeout timeout() {
      throw new AssertionError();
    }
  }

  static final class TestDelegatingService implements ExecutorService {
    private final ExecutorService delegate;
    int submissions = 0;

    public TestDelegatingService(ExecutorService delegate) {
      this.delegate = delegate;
    }

    @Override public void shutdown() {
      delegate.shutdown();
    }

    @Override public List<Runnable> shutdownNow() {
      throw new AssertionError("Not implemented.");
    }

    @Override public boolean isShutdown() {
      return delegate.isShutdown();
    }

    @Override public boolean isTerminated() {
      throw new AssertionError("Not implemented.");
    }

    @Override public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
      return delegate.awaitTermination(timeout, unit);
    }

    @Override public <T> Future<T> submit(Callable<T> task) {
      throw new AssertionError("Not implemented.");
    }

    @Override public <T> Future<T> submit(Runnable task, T result) {
      throw new AssertionError("Not implemented.");
    }

    @Override public Future<?> submit(Runnable task) {
      submissions++;
      return delegate.submit(task);
    }

    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
        TimeUnit unit) {
      throw new AssertionError("Not implemented.");
    }

    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
      throw new AssertionError("Not implemented.");
    }

    @Override public void execute(Runnable command) {
      delegate.execute(command);
    }
  }

  private TestUtils() {
  }
}
