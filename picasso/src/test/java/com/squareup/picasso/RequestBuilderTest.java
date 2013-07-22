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

import android.R;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockTarget;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RequestBuilderTest {

  @Mock Picasso picasso;
  @Captor ArgumentCaptor<Request> requestCaptor;

  @Before public void shutUp() throws Exception {
    initMocks(this);
  }

  @Test
  public void getOnMainCrashes() throws Exception {
    try {
      new RequestBuilder(picasso, URI_1, 0).get();
      fail("Calling get() on main thread should throw exception");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void getReturnsNullIfNullUriAndResourceId() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final Bitmap[] result = new Bitmap[1];

    new Thread(new Runnable() {
      @Override public void run() {
        try {
          result[0] = new RequestBuilder(picasso, null, 0).get();
        } catch (IOException e) {
          fail(e.getMessage());
        } finally {
          latch.countDown();
        }
      }
    }).start();
    latch.await();

    assertThat(result[0]).isNull();
    verifyZeroInteractions(picasso);
  }

  @Test public void fetchSubmitsFetchRequest() throws Exception {
    new RequestBuilder(picasso, URI_1, 0).fetch();
    verify(picasso).submit(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isInstanceOf(FetchRequest.class);
  }

  @Test
  public void intoTargetWithNullThrows() throws Exception {
    try {
      new RequestBuilder(picasso, URI_1, 0).into((Target) null);
      fail("Calling into() with null Target should throw exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void intoTargetWithNullUriAndResourceIdSkipsAndCancels() throws Exception {
    Target target = mockTarget();
    new RequestBuilder(picasso, null, 0).into(target);
    verify(picasso).cancelRequest(target);
    verifyNoMoreInteractions(picasso);
  }

  @Test
  public void intTargetWithQuickMemoryCacheCheckDoesNotSubmit() throws Exception {
    when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(BITMAP_1);
    Target target = mockTarget();
    new RequestBuilder(picasso, URI_1, 0).into(target);
    verify(target).onSuccess(BITMAP_1, MEMORY);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).submit(any(Request.class));
  }

  @Test
  public void intoTargetAndNotInCacheSubmitsTargetRequest() throws Exception {
    Target target = mockTarget();
    new RequestBuilder(picasso, URI_1, 0).into(target);
    verify(picasso).submit(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isInstanceOf(TargetRequest.class);
  }

  @Test
  public void intoImageViewWithNullThrows() throws Exception {
    try {
      new RequestBuilder(picasso, URI_1, 0).into((ImageView) null);
      fail("Calling into() with null ImageView should throw exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void intoImageViewWithNullUriAndResourceIdSkipsAndCancels() throws Exception {
    ImageView target = mockImageViewTarget();
    new RequestBuilder(picasso, null, 0).into(target);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).quickMemoryCacheCheck(anyString());
    verify(picasso, never()).submit(any(Request.class));
  }

  @Test
  public void intoImageViewWithQuickMemoryCacheCheckDoesNotSubmit() throws Exception {
    Picasso picasso =
        spy(new Picasso(Robolectric.application, mock(Dispatcher.class), Cache.NONE, null,
            mock(Stats.class), true));
    when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(BITMAP_1);
    ImageView target = mockImageViewTarget();
    new RequestBuilder(picasso, URI_1, 0).into(target);
    verify(picasso).cancelRequest(target);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(picasso, never()).submit(any(Request.class));
  }

  @Test
  public void intoImageViewSetsPlaceholderDrawable() throws Exception {
    Picasso picasso =
        spy(new Picasso(Robolectric.application, mock(Dispatcher.class), Cache.NONE, null,
            mock(Stats.class), true));
    ImageView target = mockImageViewTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestBuilder(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(picasso).submit(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isInstanceOf(ImageViewRequest.class);
  }

  @Test
  public void intoImageViewSetsPlaceholderWithResourceId() throws Exception {
    Picasso picasso =
        spy(new Picasso(Robolectric.application, mock(Dispatcher.class), Cache.NONE, null,
            mock(Stats.class), true));
    ImageView target = mockImageViewTarget();
    new RequestBuilder(picasso, URI_1, 0).placeholder(R.drawable.picture_frame).into(target);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(picasso).submit(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isInstanceOf(ImageViewRequest.class);
  }

  @Test
  public void intoImageViewAndNotInCacheSubmitsImageViewRequest() throws Exception {
    ImageView target = mockImageViewTarget();
    new RequestBuilder(picasso, URI_1, 0).into(target);
    verify(picasso).submit(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isInstanceOf(ImageViewRequest.class);
  }

  @Test public void invalidPlaceholderImage() throws Exception {
    try {
      new RequestBuilder().placeholder(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().placeholder(1).placeholder(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().placeholder(new ColorDrawable(0)).placeholder(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidErrorImage() throws Exception {
    try {
      new RequestBuilder().error(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().error(null);
      fail("Null drawable should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().error(1).error(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().error(new ColorDrawable(0)).error(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  public void fitAndResizeMutualExclusivity() throws Exception {
    try {
      new RequestBuilder().resize(10, 10).fit();
      fail("Fit cannot be called after resize.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().fit().resize(10, 10);
      fail("Resize cannot be called after fit.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test(expected = IllegalStateException.class)
  public void resizeCanOnlyBeCalledOnce() throws Exception {
    new RequestBuilder().resize(10, 10).resize(5, 5);
  }

  @Test public void defaultValuesIgnored() throws Exception {
    RequestBuilder b = new RequestBuilder();
    b.scale(1);
    assertThat(b.options).isNull();
    b.scale(1, 1);
    assertThat(b.options).isNull();
    b.rotate(0);
    assertThat(b.options).isNull();
    b.rotate(0, 40, 10);
    assertThat(b.options).isNull();
  }

  @Test public void invalidResize() throws Exception {
    try {
      new RequestBuilder().resize(-1, 10);
      fail("Negative width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().resize(10, -1);
      fail("Negative height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().resize(0, 10);
      fail("Zero width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().resize(10, 0);
      fail("Zero height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void invalidScale() throws Exception {
    try {
      new RequestBuilder().scale(0);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().scale(0, 1);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().scale(1, 0);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void invalidCenterCrop() throws Exception {
    try {
      new RequestBuilder().centerCrop();
      fail("Center crop without resize should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().resize(10, 10).centerInside().centerCrop();
      fail("Calling center crop after center inside should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidCenterInside() throws Exception {
    try {
      new RequestBuilder().centerInside();
      fail("Center inside without resize should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().resize(10, 10).centerInside().centerCrop();
      fail("Calling center inside after center crop should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullTransformationsInvalid() throws Exception {
    new RequestBuilder().transform(null);
  }

  @Test public void nullTargetsInvalid() throws Exception {
    try {
      new RequestBuilder().into((ImageView) null);
      fail("Null ImageView should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().into((Target) null);
      fail("Null Target should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }
}
