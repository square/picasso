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
import static com.squareup.picasso.Picasso.RequestTransformer.IDENTITY;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.TRANSFORM_REQUEST_ANSWER;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockCallback;
import static com.squareup.picasso.TestUtils.mockFitImageViewTarget;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockTarget;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RequestCreatorTest {

  @Mock Picasso picasso;
  @Captor ArgumentCaptor<Action> actionCaptor;

  @Before public void shutUp() throws Exception {
    initMocks(this);
    when(picasso.transformRequest(any(Request.class))).thenAnswer(TRANSFORM_REQUEST_ANSWER);
  }

  @Test
  public void getOnMainCrashes() throws Exception {
    try {
      new RequestCreator(picasso, URI_1, 0).get();
      fail("Calling get() on main thread should throw exception");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void loadWithShutdownCrashes() throws Exception {
    picasso.shutdown = true;
    try {
      new RequestCreator(picasso, URI_1, 0).fetch();
      fail("Should have crashed with a shutdown picasso.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void getReturnsNullIfNullUriAndResourceId() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final Bitmap[] result = new Bitmap[1];

    new Thread(new Runnable() {
      @Override public void run() {
        try {
          result[0] = new RequestCreator(picasso, null, 0).get();
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
    new RequestCreator(picasso, URI_1, 0).fetch();
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(FetchAction.class);
  }

  @Test public void fetchWithFitThrows() throws Exception {
    try {
      new RequestCreator(picasso, URI_1, 0).fit().fetch();
      fail("Calling fetch() with fit() should throw an exception");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void intoTargetWithNullThrows() throws Exception {
    try {
      new RequestCreator(picasso, URI_1, 0).into((Target) null);
      fail("Calling into() with null Target should throw exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void intoTargetWithFitThrows() throws Exception {
    try {
      Target target = mockTarget();
      new RequestCreator(picasso, URI_1, 0).fit().into(target);
      fail("Calling into() target with fit() should throw exception");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void intoTargetWithNullUriAndResourceIdSkipsAndCancels() throws Exception {
    Target target = mockTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, null, 0).placeholder(placeHolderDrawable).into(target);
    verify(picasso).cancelRequest(target);
    verify(target).onPrepareLoad(placeHolderDrawable);
    verifyNoMoreInteractions(picasso);
  }

  @Test
  public void intoTargetWithQuickMemoryCacheCheckDoesNotSubmit() throws Exception {
    when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(BITMAP_1);
    Target target = mockTarget();
    new RequestCreator(picasso, URI_1, 0).into(target);
    verify(target).onBitmapLoaded(BITMAP_1, MEMORY);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
  }

  @Test
  public void intoTargetAndSkipMemoryCacheDoesNotCheckMemoryCache() throws Exception {
    Target target = mockTarget();
    new RequestCreator(picasso, URI_1, 0).skipMemoryCache().into(target);
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
  }

  @Test
  public void intoTargetAndNotInCacheSubmitsTargetRequest() throws Exception {
    Target target = mockTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    verify(target).onPrepareLoad(placeHolderDrawable);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(TargetAction.class);
  }

  @Test
  public void intoImageViewWithNullThrows() throws Exception {
    try {
      new RequestCreator(picasso, URI_1, 0).into((ImageView) null);
      fail("Calling into() with null ImageView should throw exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void intoImageViewWithNullUriAndResourceIdSkipsAndCancels() throws Exception {
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, null, 0).into(target);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).quickMemoryCacheCheck(anyString());
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
  }

  @Test
  public void intoImageViewWithQuickMemoryCacheCheckDoesNotSubmit() throws Exception {
    Picasso picasso =
        spy(new Picasso(Robolectric.application, mock(Dispatcher.class), Cache.NONE, null, IDENTITY,
            mock(Stats.class), true));
    doReturn(BITMAP_1).when(picasso).quickMemoryCacheCheck(URI_KEY_1);
    ImageView target = mockImageViewTarget();
    Callback callback = mockCallback();
    new RequestCreator(picasso, URI_1, 0).into(target, callback);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(callback).onSuccess();
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
  }

  @Test
  public void intoImageViewSetsPlaceholderDrawable() throws Exception {
    Picasso picasso =
        spy(new Picasso(Robolectric.application, mock(Dispatcher.class), Cache.NONE, null, IDENTITY,
            mock(Stats.class), true));
    ImageView target = mockImageViewTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    verify(target).setImageDrawable(placeHolderDrawable);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewSetsPlaceholderWithResourceId() throws Exception {
    Picasso picasso =
        spy(new Picasso(Robolectric.application, mock(Dispatcher.class), Cache.NONE, null, IDENTITY,
            mock(Stats.class), true));
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).placeholder(R.drawable.picture_frame).into(target);
    verify(target).setImageResource(R.drawable.picture_frame);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewAndNotInCacheSubmitsImageViewRequest() throws Exception {
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).into(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewWithFitAndNoDimensionsQueuesDeferredImageViewRequest() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    when(target.getWidth()).thenReturn(0);
    when(target.getHeight()).thenReturn(0);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
    verify(picasso).defer(eq(target), any(DeferredRequestCreator.class));
  }

  @Test
  public void intoImageViewWithFitAndDimensionsQueuesImageViewRequest() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    when(target.getMeasuredWidth()).thenReturn(100);
    when(target.getMeasuredHeight()).thenReturn(100);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewAndSkipMemoryCacheDoesNotCheckMemoryCache() throws Exception {
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).skipMemoryCache().into(target);
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
  }

  @Test
  public void intoImageViewWithFitAndResizeThrows() throws Exception {
    try {
      ImageView target = mockImageViewTarget();
      new RequestCreator(picasso, URI_1, 0).fit().resize(10, 10).into(target);
      fail("Calling into() ImageView with fit() and resize() should throw exception");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidResize() throws Exception {
    try {
      new RequestCreator().resize(-1, 10);
      fail("Negative width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestCreator().resize(10, -1);
      fail("Negative height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestCreator().resize(0, 10);
      fail("Zero width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestCreator().resize(10, 0);
      fail("Zero height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void invalidCenterCrop() throws Exception {
    try {
      new RequestCreator().resize(10, 10).centerInside().centerCrop();
      fail("Calling center crop after center inside should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidCenterInside() throws Exception {
    try {
      new RequestCreator().resize(10, 10).centerInside().centerCrop();
      fail("Calling center inside after center crop should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidPlaceholderImage() throws Exception {
    try {
      new RequestCreator().placeholder(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestCreator().placeholder(1).placeholder(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestCreator().placeholder(new ColorDrawable(0)).placeholder(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidErrorImage() throws Exception {
    try {
      new RequestCreator().error(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestCreator().error(null);
      fail("Null drawable should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestCreator().error(1).error(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestCreator().error(new ColorDrawable(0)).error(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullTransformationsInvalid() throws Exception {
    new RequestCreator().transform(null);
  }

  @Test public void nullTargetsInvalid() throws Exception {
    try {
      new RequestCreator().into((ImageView) null);
      fail("Null ImageView should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestCreator().into((Target) null);
      fail("Null Target should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }
}
