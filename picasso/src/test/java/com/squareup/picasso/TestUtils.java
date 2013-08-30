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

import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.Uri;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.io.File;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static android.provider.ContactsContract.Contacts.CONTENT_URI;
import static android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY;
import static com.squareup.picasso.Utils.createKey;
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
  static final String URI_KEY_1 = createKey(new Request.Builder(URI_1).build());
  static final String URI_KEY_2 = createKey(new Request.Builder(URI_2).build());
  static final Bitmap BITMAP_1 = Bitmap.createBitmap(10, 10, null);
  static final Bitmap BITMAP_2 = Bitmap.createBitmap(15, 15, null);
  static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
  static final String FILE_KEY_1 = createKey(new Request.Builder(Uri.fromFile(FILE_1)).build());
  static final Uri FILE_1_URL = Uri.parse("file:///" + FILE_1.getPath());
  static final Uri FILE_1_URL_NO_AUTHORITY = Uri.parse("file:/" + FILE_1.getParent());
  static final Uri CONTENT_1_URL = Uri.parse("content://zip/zap/zoop.jpg");
  static final String CONTENT_KEY_1 = createKey(new Request.Builder(CONTENT_1_URL).build());
  static final Uri CONTACT_URI_1 = CONTENT_URI.buildUpon().path("1234").build();
  static final String CONTACT_KEY_1 = createKey(new Request.Builder(CONTACT_URI_1).build());
  static final Uri CONTACT_PHOTO_URI_1 =
      CONTENT_URI.buildUpon().path("1234").path(CONTENT_DIRECTORY).build();
  static final String CONTACT_PHOTO_KEY_1 = createKey(new Request.Builder(CONTACT_PHOTO_URI_1).build());
  static final int RESOURCE_ID_1 = 1;
  static final String RESOURCE_ID_KEY_1 = createKey(new Request.Builder(RESOURCE_ID_1).build());
  static final Uri ASSET_URI_1 = Uri.parse("file:///android_asset/foo/bar.png");
  static final String ASSET_KEY_1 = createKey(new Request.Builder(ASSET_URI_1).build());

  static Action mockAction(String key, Uri uri) {
    return mockAction(key, uri, null, 0);
  }

  static Action mockAction(String key, Uri uri, Object target) {
    return mockAction(key, uri, target, 0);
  }

  static Action mockAction(String key, Uri uri, Object target, int resourceId) {
    Request request = new Request.Builder(uri, resourceId).build();
    Action action = mock(Action.class);
    when(action.getKey()).thenReturn(key);
    when(action.getData()).thenReturn(request);
    when(action.getTarget()).thenReturn(target);
    when(action.getPicasso()).thenReturn(mock(Picasso.class));
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

  static ImageView mockFitImageViewTarget(boolean alive) {
    ViewTreeObserver observer = mock(ViewTreeObserver.class);
    when(observer.isAlive()).thenReturn(alive);
    ImageView mock = mock(ImageView.class);
    when(mock.getViewTreeObserver()).thenReturn(observer);
    return mock;
  }

  static Target mockTarget() {
    return mock(Target.class);
  }

  static Callback mockCallback() {
    return mock(Callback.class);
  }

  static DeferredRequestCreator mockDeferredRequestCreator() {
    return mock(DeferredRequestCreator.class);
  }

  static NetworkInfo mockNetworkInfo() {
    return mock(NetworkInfo.class);
  }

  static BitmapHunter mockHunter(String key, Bitmap result, boolean skipCache) {
    Request data = new Request.Builder(URI_1).build();
    BitmapHunter hunter = mock(BitmapHunter.class);
    when(hunter.getKey()).thenReturn(key);
    when(hunter.getResult()).thenReturn(result);
    when(hunter.getData()).thenReturn(data);
    when(hunter.shouldSkipMemoryCache()).thenReturn(skipCache);
    return hunter;
  }

  private TestUtils() {
  }
}
