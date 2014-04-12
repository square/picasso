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
package com.squareup.picasso;

import android.R;
import android.widget.RemoteViews;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;
import static com.squareup.picasso.Picasso.RequestTransformer.IDENTITY;
import static com.squareup.picasso.Utils.FADE_TIME;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RemoteViewsActionTest {

  private Picasso picasso;
  private RemoteViews remoteViews;

  @Before public void setUp() {
    picasso = createPicasso();
    remoteViews = mock(RemoteViews.class);
    when(remoteViews.getLayoutId()).thenReturn(R.layout.list_content);
  }

  @Test public void completeSetsBitmapOnRemoteViews() throws Exception {
    RemoteViewsAction action = createAction();
    action.complete(BITMAP_1, NETWORK);
    verify(remoteViews).setImageViewBitmap(1, BITMAP_1);
  }

  @Test public void errorWithNoResourceIsNoop() throws Exception {
    RemoteViewsAction action = createAction();
    action.error();
    verifyZeroInteractions(remoteViews);
  }

  @Test public void errorWithResourceSetsResource() throws Exception {
    RemoteViewsAction action = createAction(1);
    action.error();
    verify(remoteViews).setImageViewResource(1, 1);
  }

  private TestableRemoteViewsAction createAction() {
    return createAction(0);
  }

  private TestableRemoteViewsAction createAction(int errorResId) {
    return new TestableRemoteViewsAction(picasso, null, remoteViews, 1, errorResId, false,
        URI_KEY_1);
  }

  private Picasso createPicasso() {
    return new Picasso(Robolectric.application, mock(Dispatcher.class), Cache.NONE, null, IDENTITY,
        mock(Stats.class), true);
  }

  static class TestableRemoteViewsAction extends RemoteViewsAction {
    TestableRemoteViewsAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
        int errorResId, boolean skipCache, String key) {
      super(picasso, data, remoteViews, viewId, errorResId, skipCache, FADE_TIME, key);
    }

    @Override void update() {
    }
  }
}
