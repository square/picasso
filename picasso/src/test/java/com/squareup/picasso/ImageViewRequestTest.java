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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Request.LoadedFrom.MEMORY;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockCallback;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class) @Config(manifest = Config.NONE)
public class ImageViewRequestTest {

  @Test(expected = AssertionError.class)
  public void throwsErrorWithNullResult() throws Exception {
    ImageViewRequest request =
        new ImageViewRequest(mock(Picasso.class), URI_1, 0, mockImageViewTarget(), null, null,
            false, false, 0, null, URI_KEY_1, null);
    request.complete(null, MEMORY);
  }

  @Test
  public void invokesTargetAndCallbackSuccessIfTargetIsNotNull() throws Exception {
    Picasso picasso =
        new Picasso(Robolectric.application, mock(Downloader.class), mock(Dispatcher.class),
            Cache.NONE, null, mock(Stats.class), true);
    ImageView target = mockImageViewTarget();
    Callback callback = mockCallback();
    ImageViewRequest request =
        new ImageViewRequest(picasso, mock(Uri.class), 0, target, null, null, false, false, 0, null,
            "", callback);
    request.complete(BITMAP_1, MEMORY);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(callback).onSuccess();
  }

  @Test
  public void invokesTargetAndCallbackErrorIfTargetIsNotNullWithErrorResourceId() throws Exception {
    ImageView target = mockImageViewTarget();
    Callback callback = mockCallback();
    Picasso mock = mock(Picasso.class);
    ImageViewRequest request =
        new ImageViewRequest(mock, mock(Uri.class), 0, target, null, null, false, false,
            RESOURCE_ID_1, null, "", callback);
    request.error();
    verify(target).setImageResource(RESOURCE_ID_1);
    verify(callback).onError();
  }

  @Test
  public void invokesErrorIfTargetIsNotNullWithErrorResourceId() throws Exception {
    ImageView target = mockImageViewTarget();
    Callback callback = mockCallback();
    Picasso mock = mock(Picasso.class);
    ImageViewRequest request =
        new ImageViewRequest(mock, mock(Uri.class), 0, target, null, null, false, false,
            RESOURCE_ID_1, null, "", callback);
    request.error();
    verify(target).setImageResource(RESOURCE_ID_1);
    verify(callback).onError();
  }

  @Test
  public void invokesErrorIfTargetIsNotNullWithErrorDrawable() throws Exception {
    Drawable errorDrawable = mock(Drawable.class);
    ImageView target = mockImageViewTarget();
    Callback callback = mockCallback();
    Picasso mock = mock(Picasso.class);
    ImageViewRequest request =
        new ImageViewRequest(mock, mock(Uri.class), 0, target, null, null, false, false, 0,
            errorDrawable, "", callback);
    request.error();
    verify(target).setImageDrawable(errorDrawable);
    verify(callback).onError();
  }
}
