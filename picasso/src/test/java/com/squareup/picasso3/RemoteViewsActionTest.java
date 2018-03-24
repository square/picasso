/*
 * Copyright (C) 2014 Square, Inc.
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
import android.widget.ImageView;
import android.widget.RemoteViews;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso3.Picasso.LoadedFrom.NETWORK;
import static com.squareup.picasso3.TestUtils.NO_HANDLERS;
import static com.squareup.picasso3.TestUtils.NO_TRANSFORMERS;
import static com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY;
import static com.squareup.picasso3.TestUtils.URI_1;
import static com.squareup.picasso3.TestUtils.URI_KEY_1;
import static com.squareup.picasso3.TestUtils.makeBitmap;
import static com.squareup.picasso3.TestUtils.mockCallback;
import static com.squareup.picasso3.TestUtils.mockImageViewTarget;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class) //
public class RemoteViewsActionTest {

  private Picasso picasso;
  private RemoteViews remoteViews;

  @Before public void setUp() {
    picasso = createPicasso();
    remoteViews = mock(RemoteViews.class);
    when(remoteViews.getLayoutId()).thenReturn(android.R.layout.list_content);
  }

  @Test public void completeSetsBitmapOnRemoteViews() {
    Callback callback = mockCallback();
    Bitmap bitmap = makeBitmap();
    RemoteViewsAction action = createAction(callback);
    action.complete(bitmap, NETWORK);
    verify(remoteViews).setImageViewBitmap(1, bitmap);
    verify(callback).onSuccess();
  }

  @Test public void errorWithNoResourceIsNoop() {
    Callback callback = mockCallback();
    RemoteViewsAction action = createAction(callback);
    Exception e = new RuntimeException();
    action.error(e);
    verifyZeroInteractions(remoteViews);
    verify(callback).onError(e);
  }

  @Test public void errorWithResourceSetsResource() {
    Callback callback = mockCallback();
    RemoteViewsAction action = createAction(1, callback);
    Exception e = new RuntimeException();
    action.error(e);
    verify(remoteViews).setImageViewResource(1, 1);
    verify(callback).onError(e);
  }

  @Test public void clearsCallbackOnCancel() {
    Picasso picasso = mock(Picasso.class);
    ImageView target = mockImageViewTarget();
    Callback callback = mockCallback();
    ImageViewAction request =
        new ImageViewAction(picasso, target, new Request.Builder(URI_1).build(), 0, null, URI_KEY_1,
            null, callback, false);
    request.cancel();
    assertThat(request.callback).isNull();
  }

  private TestableRemoteViewsAction createAction(Callback callback) {
    return createAction(0, callback);
  }

  private TestableRemoteViewsAction createAction(int errorResId, Callback callback) {
    return new TestableRemoteViewsAction(picasso, new Request.Builder(URI_1).build(), remoteViews,
        1, errorResId, null, URI_KEY_1, callback);
  }

  private Picasso createPicasso() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    PlatformLruCache cache = new PlatformLruCache(0);
    return new Picasso(RuntimeEnvironment.application, dispatcher, UNUSED_CALL_FACTORY, null, cache,
        null, NO_TRANSFORMERS, NO_HANDLERS, mock(Stats.class), ARGB_8888, false, false);
  }

  static final class TestableRemoteViewsAction extends RemoteViewsAction {
    TestableRemoteViewsAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
        int errorResId, String tag, String key, Callback callback) {
      super(picasso, data, remoteViews, viewId, errorResId, tag, key, callback);
    }

    @Override void update() {
    }
  }
}
