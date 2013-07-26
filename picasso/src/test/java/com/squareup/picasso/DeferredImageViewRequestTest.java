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

import android.view.ViewTreeObserver;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockFitImageViewTarget;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@SuppressWarnings("deprecation")
public class DeferredImageViewRequestTest {

  @Test public void initAttachesLayoutListener() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    ViewTreeObserver observer = target.getViewTreeObserver();
    DeferredImageViewRequest request =
        new DeferredImageViewRequest(mock(Picasso.class), URI_1, 0, target, null, null, false,
            false, 0, null, URI_KEY_1, null);
    verify(observer).addOnGlobalLayoutListener(request);
  }

  @Test public void cancelRemovesLayoutListener() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    ViewTreeObserver observer = target.getViewTreeObserver();
    DeferredImageViewRequest request =
        new DeferredImageViewRequest(mock(Picasso.class), URI_1, 0, target, null, null, false,
            false, 0, null, URI_KEY_1, null);
    request.cancel();
    verify(observer).removeGlobalOnLayoutListener(request);
  }

  @Test public void onGlobalLayoutSubmitsRequestAndCleansUp() throws Exception {
    Picasso picasso = mock(Picasso.class);

    ImageView target = mockFitImageViewTarget(true);
    when(target.getMeasuredWidth()).thenReturn(100);
    when(target.getMeasuredHeight()).thenReturn(100);

    ViewTreeObserver observer = target.getViewTreeObserver();

    DeferredImageViewRequest request =
        new DeferredImageViewRequest(picasso, URI_1, 0, target, new PicassoBitmapOptions(), null,
            false, false, 0, null, URI_KEY_1, null);

    request.onGlobalLayout();

    assertThat(request.options.targetWidth).isEqualTo(100);
    assertThat(request.options.targetHeight).isEqualTo(100);
    assertThat(request.options.inJustDecodeBounds).isTrue();
    verify(observer).removeGlobalOnLayoutListener(request);
    verify(picasso).submit(request);
  }
}
