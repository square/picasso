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
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso3.Picasso.Priority.HIGH;
import static com.squareup.picasso3.Picasso.Priority.LOW;
import static com.squareup.picasso3.Picasso.Priority.NORMAL;
import static com.squareup.picasso3.RemoteViewsAction.AppWidgetAction;
import static com.squareup.picasso3.RemoteViewsAction.NotificationAction;
import static com.squareup.picasso3.TestUtils.NO_HANDLERS;
import static com.squareup.picasso3.TestUtils.NO_TRANSFORMERS;
import static com.squareup.picasso3.TestUtils.STABLE_1;
import static com.squareup.picasso3.TestUtils.STABLE_URI_KEY_1;
import static com.squareup.picasso3.TestUtils.TRANSFORM_REQUEST_ANSWER;
import static com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY;
import static com.squareup.picasso3.TestUtils.URI_1;
import static com.squareup.picasso3.TestUtils.URI_KEY_1;
import static com.squareup.picasso3.TestUtils.makeBitmap;
import static com.squareup.picasso3.TestUtils.mockCallback;
import static com.squareup.picasso3.TestUtils.mockFitImageViewTarget;
import static com.squareup.picasso3.TestUtils.mockImageViewTarget;
import static com.squareup.picasso3.TestUtils.mockNotification;
import static com.squareup.picasso3.TestUtils.mockRemoteViews;
import static com.squareup.picasso3.TestUtils.mockTarget;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class RequestCreatorTest {

  @Mock Picasso picasso;
  @Captor ArgumentCaptor<Action> actionCaptor;

  final Bitmap bitmap = makeBitmap();

  @Before public void shutUp() {
    initMocks(this);
    when(picasso.transformRequest(any(Request.class))).thenAnswer(TRANSFORM_REQUEST_ANSWER);
  }

  @Test
  public void getOnMainCrashes() throws IOException {
    try {
      new RequestCreator(picasso, URI_1, 0).get();
      fail("Calling get() on main thread should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void loadWithShutdownCrashes() {
    picasso.shutdown = true;
    try {
      new RequestCreator(picasso, URI_1, 0).fetch();
      fail("Should have crashed with a shutdown picasso.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void getReturnsNullIfNullUriAndResourceId() throws InterruptedException {
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

  @Test public void fetchSubmitsFetchRequest() {
    new RequestCreator(picasso, URI_1, 0).fetch();
    verify(picasso).submit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(FetchAction.class);
  }

  @Test public void fetchWithFitThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).fit().fetch();
      fail("Calling fetch() with fit() should throw an exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void fetchWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).fetch();
    verify(picasso).submit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(LOW);
  }

  @Test public void fetchWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH).fetch();
    verify(picasso).submit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
  }

  @Test public void fetchWithCache() {
    when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).fetch();
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
  }

  @Test public void fetchWithMemoryPolicyNoCache() {
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).fetch();
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
    verify(picasso).submit(actionCaptor.capture());
  }

  @Test
  public void intoTargetWithNullThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).into((BitmapTarget) null);
      fail("Calling into() with null Target should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoTargetWithFitThrows() {
    try {
      BitmapTarget target = mockTarget();
      new RequestCreator(picasso, URI_1, 0).fit().into(target);
      fail("Calling into() target with fit() should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void intoTargetNoPlaceholderCallsWithNull() {
    BitmapTarget target = mockTarget();
    new RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target);
    verify(target).onPrepareLoad(null);
  }

  @Test
  public void intoTargetWithNullUriAndResourceIdSkipsAndCancels() {
    BitmapTarget target = mockTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, null, 0).placeholder(placeHolderDrawable).into(target);
    verify(picasso).cancelRequest(target);
    verify(target).onPrepareLoad(placeHolderDrawable);
    verifyNoMoreInteractions(picasso);
  }

  @Test
  public void intoTargetWithQuickMemoryCacheCheckDoesNotSubmit() {
    when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
    BitmapTarget target = mockTarget();
    new RequestCreator(picasso, URI_1, 0).into(target);
    verify(target).onBitmapLoaded(bitmap, MEMORY);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
  }

  @Test
  public void intoTargetWithSkipMemoryPolicy() {
    BitmapTarget target = mockTarget();
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).into(target);
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
  }

  @Test
  public void intoTargetAndNotInCacheSubmitsTargetRequest() {
    BitmapTarget target = mockTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    verify(target).onPrepareLoad(placeHolderDrawable);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(BitmapTargetAction.class);
  }

  @Test public void targetActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
  }

  @Test public void targetActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
  }

  @Test public void targetActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo(actionCaptor.getValue());
  }

  @Test public void targetActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag").into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo("tag");
  }

  @Test
  public void intoImageViewWithNullThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).into((ImageView) null);
      fail("Calling into() with null ImageView should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoImageViewWithNullUriAndResourceIdSkipsAndCancels() {
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, null, 0).into(target);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).quickMemoryCacheCheck(anyString());
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
  }

  @Test
  public void intoImageViewWithQuickMemoryCacheCheckDoesNotSubmit() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso =
        spy(new Picasso(RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
            null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, mock(Stats.class), ARGB_8888, false,
            false));
    doReturn(bitmap).when(picasso).quickMemoryCacheCheck(URI_KEY_1);
    ImageView target = mockImageViewTarget();
    Callback callback = mockCallback();
    new RequestCreator(picasso, URI_1, 0).into(target, callback);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(callback).onSuccess();
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
  }

  @Test
  public void intoImageViewSetsPlaceholderDrawable() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso =
        spy(new Picasso(RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
            null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, mock(Stats.class), ARGB_8888, false,
            false));
    ImageView target = mockImageViewTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    verify(target).setImageDrawable(placeHolderDrawable);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewNoPlaceholderDrawable() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso =
        spy(new Picasso(RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
            null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, mock(Stats.class), ARGB_8888, false,
            false));
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target);
    verifyNoMoreInteractions(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewSetsPlaceholderWithResourceId() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso =
        spy(new Picasso(RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
            null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, mock(Stats.class), ARGB_8888, false,
            false));
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).placeholder(android.R.drawable.picture_frame)
        .into(target);
    ArgumentCaptor<Drawable> drawableCaptor = ArgumentCaptor.forClass(Drawable.class);
    verify(target).setImageDrawable(drawableCaptor.capture());
    assertThat(shadowOf(drawableCaptor.getValue()).getCreatedFromResId())
        .isEqualTo(android.R.drawable.picture_frame);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void cancelNotOnMainThreadCrashes() throws InterruptedException {
    doCallRealMethod().when(picasso).cancelRequest(any(BitmapTarget.class));
    final CountDownLatch latch = new CountDownLatch(1);
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          new RequestCreator(picasso, null, 0).into(mockTarget());
          fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ignored) {
        } finally {
          latch.countDown();
        }
      }
    }).start();
    latch.await();
  }

  @Test
  public void intoNotOnMainThreadCrashes() throws InterruptedException {
    doCallRealMethod().when(picasso).enqueueAndSubmit(any(Action.class));
    final CountDownLatch latch = new CountDownLatch(1);
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
          fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ignored) {
        } finally {
          latch.countDown();
        }
      }
    }).start();
    latch.await();
  }

  @Test
  public void intoImageViewAndNotInCacheSubmitsImageViewRequest() {
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).into(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewWithFitAndNoDimensionsQueuesDeferredImageViewRequest() {
    ImageView target = mockFitImageViewTarget(true);
    when(target.getWidth()).thenReturn(0);
    when(target.getHeight()).thenReturn(0);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
    verify(picasso).defer(eq(target), any(DeferredRequestCreator.class));
  }

  @Test
  public void intoImageViewWithFitAndDimensionsQueuesImageViewRequest() {
    ImageView target = mockFitImageViewTarget(true);
    when(target.getWidth()).thenReturn(100);
    when(target.getHeight()).thenReturn(100);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewWithSkipMemoryCachePolicy() {
    ImageView target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).into(target);
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
  }

  @Test
  public void intoImageViewWithFitAndResizeThrows() {
    try {
      ImageView target = mockImageViewTarget();
      new RequestCreator(picasso, URI_1, 0).fit().resize(10, 10).into(target);
      fail("Calling into() ImageView with fit() and resize() should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void imageViewActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
  }

  @Test public void imageViewActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
  }

  @Test public void imageViewActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo(actionCaptor.getValue());
  }

  @Test public void imageViewActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag").into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo("tag");
  }

  @Test public void intoRemoteViewsWidgetQueuesAppWidgetAction() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[] { 1, 2, 3 });
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(AppWidgetAction.class);
  }

  @Test public void intoRemoteViewsNotificationQueuesNotificationAction() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(NotificationAction.class);
  }

  @Test
  public void intoRemoteViewsNotificationWithNullRemoteViewsThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).into(null, 0, 0, mockNotification());
      fail("Calling into() with null RemoteViews should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsWidgetWithPlaceholderDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).placeholder(new ColorDrawable(0))
          .into(mockRemoteViews(), 0, new int[] { 1, 2, 3 });
      fail("Calling into() with placeholder drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsWidgetWithErrorDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).error(new ColorDrawable(0))
          .into(mockRemoteViews(), 0, new int[] { 1, 2, 3 });
      fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsNotificationWithPlaceholderDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).placeholder(new ColorDrawable(0))
          .into(mockRemoteViews(), 0, 0, mockNotification());
      fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsNotificationWithErrorDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).error(new ColorDrawable(0))
          .into(mockRemoteViews(), 0, 0, mockNotification());
      fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsWidgetWithNullRemoteViewsThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).into(null, 0, new int[] { 1, 2, 3 });
      fail("Calling into() with null RemoteViews should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsWidgetWithNullAppWidgetIdsThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, null);
      fail("Calling into() with null appWidgetIds should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsNotificationWithNullNotificationThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, (Notification) null);
      fail("Calling into() with null Notification should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsWidgetWithFitThrows() {
    try {
      RemoteViews remoteViews = mockRemoteViews();
      new RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, new int[] { 1, 2, 3 });
      fail("Calling fit() into remote views should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsNotificationWithFitThrows() {
    try {
      RemoteViews remoteViews = mockRemoteViews();
      new RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, 1, mockNotification());
      fail("Calling fit() into remote views should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void intoTargetNoResizeWithCenterInsideOrCenterCropThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).centerInside().into(mockTarget());
      fail("Center inside with unknown width should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      new RequestCreator(picasso, URI_1, 0).centerCrop().into(mockTarget());
      fail("Center inside with unknown height should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void appWidgetActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[] { 1, 2, 3 });
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
  }

  @Test public void appWidgetActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH)
        .into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
  }

  @Test public void notificationActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
  }

  @Test public void notificationActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH)
        .into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
  }

  @Test public void appWidgetActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[] { 1, 2, 3 });
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo(actionCaptor.getValue());
  }

  @Test public void appWidgetActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag")
        .into(mockRemoteViews(), 0, new int[] { 1, 2, 3 });
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo("tag");
  }

  @Test public void notificationActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo(actionCaptor.getValue());
  }

  @Test public void notificationActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag")
        .into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getTag()).isEqualTo("tag");
  }

  @Test public void nullMemoryPolicy() {
    try {
      new RequestCreator().memoryPolicy(null);
      fail("Null memory policy should throw exception.");
    } catch (NullPointerException ignored) {
    }
  }

  @Test public void nullAdditionalMemoryPolicy() {
    try {
      new RequestCreator().memoryPolicy(MemoryPolicy.NO_CACHE, (MemoryPolicy[]) null);
      fail("Null additional memory policy should throw exception.");
    } catch (NullPointerException ignored) {
    }
  }

  @Test public void nullMemoryPolicyAssholeStyle() {
    try {
      new RequestCreator().memoryPolicy(MemoryPolicy.NO_CACHE, new MemoryPolicy[] { null });
      fail("Null additional memory policy should throw exception.");
    } catch (NullPointerException ignored) {
    }
  }

  @Test public void nullNetworkPolicy() {
    try {
      new RequestCreator().networkPolicy(null);
      fail("Null network policy should throw exception.");
    } catch (NullPointerException ignored) {
    }
  }

  @Test public void nullAdditionalNetworkPolicy() {
    try {
      new RequestCreator().networkPolicy(NetworkPolicy.NO_CACHE, (NetworkPolicy[]) null);
      fail("Null additional network policy should throw exception.");
    } catch (NullPointerException ignored) {
    }
  }

  @Test public void nullNetworkPolicyAssholeStyle() {
    try {
      new RequestCreator().networkPolicy(NetworkPolicy.NO_CACHE, new NetworkPolicy[] { null });
      fail("Null additional network policy should throw exception.");
    } catch (NullPointerException ignored) {
    }
  }

  @Test public void invalidResize() {
    try {
      new RequestCreator().resize(-1, 10);
      fail("Negative width should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      new RequestCreator().resize(10, -1);
      fail("Negative height should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      new RequestCreator().resize(0, 0);
      fail("Zero dimensions should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test public void invalidCenterCrop() {
    try {
      new RequestCreator().resize(10, 10).centerInside().centerCrop();
      fail("Calling center crop after center inside should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void invalidCenterInside() {
    try {
      new RequestCreator().resize(10, 10).centerInside().centerCrop();
      fail("Calling center inside after center crop should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void invalidPlaceholderImage() {
    try {
      new RequestCreator().placeholder(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      new RequestCreator().placeholder(1).placeholder(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      new RequestCreator().placeholder(new ColorDrawable(0)).placeholder(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void invalidNoPlaceholder() {
    try {
      new RequestCreator().noPlaceholder().placeholder(new ColorDrawable(0));
      fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      new RequestCreator().noPlaceholder().placeholder(1);
      fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      new RequestCreator().placeholder(1).noPlaceholder();
      fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      new RequestCreator().placeholder(new ColorDrawable(0)).noPlaceholder();
      fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void invalidErrorImage() {
    try {
      new RequestCreator().error(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      new RequestCreator().error(null);
      fail("Null drawable should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      new RequestCreator().error(1).error(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      new RequestCreator().error(new ColorDrawable(0)).error(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void invalidPriority() {
    try {
      new RequestCreator().priority(null);
      fail("Null priority should throw exception.");
    } catch (NullPointerException ignored) {
    }
    try {
      new RequestCreator().priority(LOW).priority(HIGH);
      fail("Two priorities should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }


  @Test public void invalidTag() {
    try {
      new RequestCreator().tag(null);
      fail("Null tag should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      new RequestCreator().tag("tag1").tag("tag2");
      fail("Two tags should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test(expected = NullPointerException.class)
  public void nullTransformationsInvalid() {
    new RequestCreator().transform((Transformation) null);
  }

  @Test(expected = NullPointerException.class)
  public void nullTransformationListInvalid() {
    new RequestCreator().transform((List<Transformation>) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullKeyTransformationInvalid() {
    new RequestCreator().transform(new Transformation() {
      @Override public RequestHandler.Result transform(RequestHandler.Result source) {
        return source;
      }

      @Override public String key() {
        return null;
      }
    });
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullKeyInTransformationListInvalid() {
    List<? extends Transformation> transformations =
        Collections.singletonList(new Transformation() {
          @Override public RequestHandler.Result transform(RequestHandler.Result source) {
            return source;
          }

          @Override public String key() {
            return null;
          }
        });
    new RequestCreator().transform(transformations);
  }

  @Test public void transformationListImplementationValid() {
    List<TestTransformation> transformations =
        Collections.singletonList(new TestTransformation("test"));
    new RequestCreator().transform(transformations);
    // TODO verify something!
  }

  @Test public void nullTargetsInvalid() {
    try {
      new RequestCreator().into((ImageView) null);
      fail("Null ImageView should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      new RequestCreator().into((BitmapTarget) null);
      fail("Null Target should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test public void imageViewActionWithStableKey() {
    new RequestCreator(picasso, URI_1, 0).stableKey(STABLE_1).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.key).isEqualTo(STABLE_URI_KEY_1);
  }

  @Test public void imageViewActionWithStableKeyNull() {
    new RequestCreator(picasso, URI_1, 0).stableKey(null).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.key).isEqualTo(URI_KEY_1);
  }

  @Test public void notPurgeable() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.purgeable).isFalse();
  }

  @Test public void purgeable() {
    new RequestCreator(picasso, URI_1, 0).purgeable().into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.purgeable).isTrue();
  }
}
