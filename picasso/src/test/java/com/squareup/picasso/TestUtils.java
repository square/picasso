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
package com.squareup.picasso;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.provider.ContactsContract.Contacts.CONTENT_URI;
import static android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.Picasso.Priority;
import static com.squareup.picasso.Utils.createKey;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestUtils {
  static final Answer<Object> TRANSFORM_REQUEST_ANSWER = new Answer<Object>() {
    @Override public Object answer(InvocationOnMock invocation) throws Throwable {
      return invocation.getArguments()[0];
    }
  };
  static final Uri URI_1 = Uri.parse("http://example.com/1.png");
  static final Uri URI_2 = Uri.parse("http://example.com/2.png");
  static final String STABLE_1 = "stableExampleKey1";
  static final String URI_KEY_1 = createKey(new Request.Builder(URI_1).build());
  static final String URI_KEY_2 = createKey(new Request.Builder(URI_2).build());
  static final String STABLE_URI_KEY_1 = createKey(new Request.Builder(URI_1).stableKey(STABLE_1).build());
  static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
  static final String FILE_KEY_1 = createKey(new Request.Builder(Uri.fromFile(FILE_1)).build());
  static final Uri FILE_1_URL = Uri.parse("file:///" + FILE_1.getPath());
  static final Uri FILE_1_URL_NO_AUTHORITY = Uri.parse("file:/" + FILE_1.getParent());
  static final Uri MEDIA_STORE_CONTENT_1_URL = Uri.parse("content://media/external/images/media/1");
  static final String MEDIA_STORE_CONTENT_KEY_1 =
      createKey(new Request.Builder(MEDIA_STORE_CONTENT_1_URL).build());
  static final Uri CONTENT_1_URL = Uri.parse("content://zip/zap/zoop.jpg");
  static final String CONTENT_KEY_1 = createKey(new Request.Builder(CONTENT_1_URL).build());
  static final Uri CONTACT_URI_1 = CONTENT_URI.buildUpon().appendPath("1234").build();
  static final String CONTACT_KEY_1 = createKey(new Request.Builder(CONTACT_URI_1).build());
  static final Uri CONTACT_PHOTO_URI_1 =
      CONTENT_URI.buildUpon().appendPath("1234").appendPath(CONTENT_DIRECTORY).build();
  static final String CONTACT_PHOTO_KEY_1 =
      createKey(new Request.Builder(CONTACT_PHOTO_URI_1).build());
  static final int RESOURCE_ID_1 = 1;
  static final String RESOURCE_ID_KEY_1 = createKey(new Request.Builder(RESOURCE_ID_1).build());
  static final Uri ASSET_URI_1 = Uri.parse("file:///android_asset/foo/bar.png");
  static final String ASSET_KEY_1 = createKey(new Request.Builder(ASSET_URI_1).build());
  static final String RESOURCE_PACKAGE = "com.squareup.picasso";
  static final String RESOURCE_TYPE = "drawable";
  static final String RESOURCE_NAME = "foo";
  static final Uri RESOURCE_ID_URI = new Uri.Builder().scheme(SCHEME_ANDROID_RESOURCE)
      .authority(RESOURCE_PACKAGE)
      .appendPath(Integer.toString(RESOURCE_ID_1))
      .build();
  static final String RESOURCE_ID_URI_KEY = createKey(new Request.Builder(RESOURCE_ID_URI).build());
  static final Uri RESOURCE_TYPE_URI = new Uri.Builder().scheme(SCHEME_ANDROID_RESOURCE)
      .authority(RESOURCE_PACKAGE)
      .appendPath(RESOURCE_TYPE)
      .appendPath(RESOURCE_NAME)
      .build();
  static final String RESOURCE_TYPE_URI_KEY =
      createKey(new Request.Builder(RESOURCE_TYPE_URI).build());
  static final Uri CUSTOM_URI = Uri.parse("foo://bar");
  static final String CUSTOM_URI_KEY = createKey(new Request.Builder(CUSTOM_URI).build());

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

  static Action mockAction(String key, Uri uri) {
    return mockAction(key, uri, null, 0, null, null);
  }

  static Action mockAction(String key, Uri uri, Object target) {
    return mockAction(key, uri, target, 0, null, null);
  }

  static Action mockAction(String key, Uri uri, Priority priority) {
    return mockAction(key, uri, null, 0, priority, null);
  }

  static Action mockAction(String key, Uri uri, String tag) {
    return mockAction(key, uri, null, 0, null, tag);
  }

  static Action mockAction(String key, Uri uri, Object target, String tag) {
    return mockAction(key, uri, target, 0, null, tag);
  }

  static Action mockAction(String key, Uri uri, Object target, int resourceId) {
    return mockAction(key, uri, target, resourceId, null, null);
  }

  static Action mockAction(String key, Uri uri, Object target, int resourceId, Priority priority,
                           String tag) {
    Request request = new Request.Builder(uri, resourceId, ARGB_8888).build();
    return mockAction(key, request, target, priority, tag);
  }

  static Action mockAction(String key, Request request) {
    return mockAction(key, request, null, null, null);
  }

  static Action mockAction(String key, Request request, Object target, Priority priority,
                           String tag) {
    Action action = mock(Action.class);
    when(action.getKey()).thenReturn(key);
    when(action.getRequest()).thenReturn(request);
    when(action.getTarget()).thenReturn(target);
    when(action.getPriority()).thenReturn(priority != null ? priority : Priority.NORMAL);
    when(action.getTag()).thenReturn(tag != null ? tag : action);

    Picasso picasso = mockPicasso();
    when(action.getPicasso()).thenReturn(picasso);

    return action;
  }

  static Action mockCanceledAction() {
    Action action = mock(Action.class);
    action.cancelled = true;
    when(action.isCancelled()).thenReturn(true);
    return action;
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

  static Target mockTarget() {
    return mock(Target.class);
  }

  static RemoteViewsAction.RemoteViewsTarget mockRemoteViewsTarget() {
    return mock(RemoteViewsAction.RemoteViewsTarget.class);
  }

  static Callback mockCallback() {
    return mock(Callback.class);
  }

  static DeferredRequestCreator mockDeferredRequestCreator() {
    return mock(DeferredRequestCreator.class);
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

  static InputStream mockInputStream() throws IOException {
    return mock(InputStream.class);
  }

  static BitmapHunter mockHunter(String key, Bitmap result, boolean skipCache) {
    return mockHunter(key, result, skipCache, null);
  }

  static BitmapHunter mockHunter(String key, Bitmap result, boolean skipCache, Action action) {
    int memoryPolicy = skipCache ? MemoryPolicy.NO_STORE.index | MemoryPolicy.NO_CACHE.index : 0;
    return mockHunter(key, result, memoryPolicy, action);
  }

  static BitmapHunter mockHunter(String key, Bitmap result, int memoryPolicy, Action action) {
    Request data = new Request.Builder(URI_1).build();
    BitmapHunter hunter = mock(BitmapHunter.class);
    when(hunter.getKey()).thenReturn(key);
    when(hunter.getResult()).thenReturn(result);
    when(hunter.getData()).thenReturn(data);
    when(hunter.getMemoryPolicy()).thenReturn(memoryPolicy);
    when(hunter.getAction()).thenReturn(action);
    Picasso picasso = mockPicasso();
    when(hunter.getPicasso()).thenReturn(picasso);

    return hunter;
  }

  static Picasso mockPicasso() {
    // Mock a RequestHandler that can handle any request.
    RequestHandler requestHandler = mock(RequestHandler.class);
    try {
      Bitmap defaultResult = makeBitmap();
      RequestHandler.Result result = new RequestHandler.Result(defaultResult, MEMORY);
      when(requestHandler.load(any(Request.class), anyInt())).thenReturn(result);
      when(requestHandler.canHandleRequest(any(Request.class))).thenReturn(true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return mockPicasso(requestHandler);
  }

  static Picasso mockPicasso(RequestHandler requestHandler) {
    Picasso picasso = mock(Picasso.class);
    when(picasso.getRequestHandlers()).thenReturn(Arrays.asList(requestHandler));
    return picasso;
  }

  static Bitmap makeBitmap() {
    return makeBitmap(10, 10);
  }

  static Bitmap makeBitmap(int width, int height) {
    return Bitmap.createBitmap(width, height, null);
  }

  private TestUtils() {
  }
}
