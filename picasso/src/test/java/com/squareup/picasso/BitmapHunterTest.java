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
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.FutureTask;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBitmap;
import org.robolectric.shadows.ShadowMatrix;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.squareup.picasso.BitmapHunter.forRequest;
import static com.squareup.picasso.BitmapHunter.transformResult;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.Picasso.Priority.HIGH;
import static com.squareup.picasso.Picasso.Priority.LOW;
import static com.squareup.picasso.Picasso.Priority.NORMAL;
import static com.squareup.picasso.TestUtils.ASSET_KEY_1;
import static com.squareup.picasso.TestUtils.ASSET_URI_1;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.CONTACT_KEY_1;
import static com.squareup.picasso.TestUtils.CONTACT_URI_1;
import static com.squareup.picasso.TestUtils.CONTENT_1_URL;
import static com.squareup.picasso.TestUtils.CONTENT_KEY_1;
import static com.squareup.picasso.TestUtils.CUSTOM_URI;
import static com.squareup.picasso.TestUtils.CUSTOM_URI_KEY;
import static com.squareup.picasso.TestUtils.FILE_1_URL;
import static com.squareup.picasso.TestUtils.FILE_KEY_1;
import static com.squareup.picasso.TestUtils.MEDIA_STORE_CONTENT_1_URL;
import static com.squareup.picasso.TestUtils.MEDIA_STORE_CONTENT_KEY_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_KEY_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_URI;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_URI_KEY;
import static com.squareup.picasso.TestUtils.RESOURCE_TYPE_URI;
import static com.squareup.picasso.TestUtils.RESOURCE_TYPE_URI_KEY;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockAction;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockPicasso;
import static org.fest.assertions.api.ANDROID.assertThat;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.entry;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BitmapHunterTest {

  @Mock Context context;
  @Mock Picasso picasso;
  @Mock Cache cache;
  @Mock Stats stats;
  @Mock Dispatcher dispatcher;
  @Mock Downloader downloader;

  @Before public void setUp() throws Exception {
    initMocks(this);
  }

  @Test public void nullDecodeResponseIsError() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, null);
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void runWithResultDispatchComplete() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, BITMAP_1);
    hunter.run();
    verify(dispatcher).dispatchComplete(hunter);
  }

  @Test public void runWithNoResultDispatchFailed() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action);
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void responseExcpetionDispatchFailed() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, null,
        new Downloader.ResponseException("Test"));
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void outOfMemoryDispatchFailed() throws Exception {
    when(stats.createSnapshot()).thenReturn(mock(StatsSnapshot.class));

    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new OOMBitmapHunter(picasso, dispatcher, cache, stats, action);
    try {
      hunter.run();
    } catch (Throwable t) {
      Exception exception = hunter.getException();
      verify(dispatcher).dispatchFailed(hunter);
      verify(stats).createSnapshot();
      assertThat(hunter.getResult()).isNull();
      assertThat(exception).isNotNull();
      assertThat(exception.getCause()).isInstanceOf(OutOfMemoryError.class);
    }
  }

  @Test public void runWithIoExceptionDispatchRetry() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, null,
        new IOException());
    hunter.run();
    verify(dispatcher).dispatchRetry(hunter);
  }

  @Test public void huntDecodesWhenNotInCache() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    TestableBitmapHunter hunter =
        new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, BITMAP_1);

    Bitmap result = hunter.hunt();
    verify(cache).get(URI_KEY_1);
    verify(hunter.requestHandler).load(action.getRequest());
    assertThat(result).isEqualTo(BITMAP_1);
  }

  @Test public void huntReturnsWhenResultInCache() throws Exception {
    when(cache.get(URI_KEY_1)).thenReturn(BITMAP_1);
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    TestableBitmapHunter hunter =
        new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, BITMAP_1);

    Bitmap result = hunter.hunt();
    verify(cache).get(URI_KEY_1);
    verify(hunter.requestHandler, never()).load(action.getRequest());
    assertThat(result).isEqualTo(BITMAP_1);
  }

  @Test public void huntUnrecognizedUri() throws Exception {
    Action action = mockAction(CUSTOM_URI_KEY, CUSTOM_URI);
    BitmapHunter hunter = forRequest(picasso, dispatcher, cache, stats, action);
    try {
      hunter.hunt();
      fail("Unrecognized URI should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void huntDecodesWithRequestHandler() throws Exception {
    Action action = mockAction(CUSTOM_URI_KEY, CUSTOM_URI);
    BitmapHunter hunter = forRequest(mockPicasso(new CustomRequestHandler()), dispatcher,
        cache, stats, action);
    Bitmap result = hunter.hunt();
    assertThat(result).isEqualTo(BITMAP_1);
  }

  @Test public void attachSingleRequest() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    assertThat(hunter.action).isEqualTo(action1);
    hunter.detach(action1);
    hunter.attach(action1);
    assertThat(hunter.action).isEqualTo(action1);
    assertThat(hunter.actions).isNull();
  }

  @Test public void attachMultipleRequests() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    assertThat(hunter.actions).isNull();
    hunter.attach(action2);
    assertThat(hunter.actions).isNotNull().hasSize(1);
  }

  @Test public void detachSingleRequest() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action);
    assertThat(hunter.action).isNotNull();
    hunter.detach(action);
    assertThat(hunter.action).isNull();
  }

  @Test public void detachMutlipleRequests() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action);
    hunter.attach(action2);
    hunter.detach(action2);
    assertThat(hunter.action).isNotNull();
    assertThat(hunter.actions).isNotNull().isEmpty();
    hunter.detach(action);
    assertThat(hunter.action).isNull();
  }

  @Test public void cancelSingleRequest() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    hunter.future = new FutureTask<Object>(mock(Runnable.class), mock(Object.class));
    assertThat(hunter.isCancelled()).isFalse();
    assertThat(hunter.cancel()).isFalse();
    hunter.detach(action1);
    assertThat(hunter.cancel()).isTrue();
    assertThat(hunter.isCancelled()).isTrue();
  }

  @Test public void cancelMultipleRequests() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    hunter.future = new FutureTask<Object>(mock(Runnable.class), mock(Object.class));
    hunter.attach(action2);
    assertThat(hunter.isCancelled()).isFalse();
    assertThat(hunter.cancel()).isFalse();
    hunter.detach(action1);
    hunter.detach(action2);
    assertThat(hunter.cancel()).isTrue();
    assertThat(hunter.isCancelled()).isTrue();
  }

  // ---------------------------------------

  @Test public void forContentProviderRequest() throws Exception {
    Action action = mockAction(CONTENT_KEY_1, CONTENT_1_URL);
    BitmapHunter hunter = forRequest(mockPicasso(new ContentStreamRequestHandler(context)),
        dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ContentStreamRequestHandler.class);
  }

  @Test public void forMediaStoreRequest() throws Exception {
    Action action = mockAction(MEDIA_STORE_CONTENT_KEY_1, MEDIA_STORE_CONTENT_1_URL);
    BitmapHunter hunter = forRequest(mockPicasso(new MediaStoreRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(MediaStoreRequestHandler.class);
  }

  @Test public void forContactsPhotoRequest() throws Exception {
    Action action = mockAction(CONTACT_KEY_1, CONTACT_URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new ContactsPhotoRequestHandler(context)),
        dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ContactsPhotoRequestHandler.class);
  }

  @Test public void forNetworkRequest() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(NetworkRequestHandler.class);
  }

  @Test public void forFileWithAuthorityRequest() throws Exception {
    Action action = mockAction(FILE_KEY_1, FILE_1_URL);
    BitmapHunter hunter = forRequest(mockPicasso(new FileRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(FileRequestHandler.class);
  }

  @Test public void forAndroidResourceRequest() throws Exception {
    Action action = mockAction(RESOURCE_ID_KEY_1, null, null, RESOURCE_ID_1);
    BitmapHunter hunter = forRequest(mockPicasso(new ResourceRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler.class);
  }

  @Test public void forAndroidResourceUriWithId() throws Exception {
    Action action = mockAction(RESOURCE_ID_URI_KEY, RESOURCE_ID_URI);
    BitmapHunter hunter = forRequest(mockPicasso(new ResourceRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler.class);
  }

  @Test public void forAndroidResourceUriWithType() throws Exception {
    Action action = mockAction(RESOURCE_TYPE_URI_KEY, RESOURCE_TYPE_URI);
    BitmapHunter hunter = forRequest(mockPicasso(new ResourceRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler.class);
  }

  @Test public void forAssetRequest() {
    Action action = mockAction(ASSET_KEY_1, ASSET_URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new AssetRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(AssetRequestHandler.class);
  }

  @Test public void forFileWithNoPathSegments() {
    Action action = mockAction("keykeykey", Uri.fromFile(new File("/")));
    BitmapHunter hunter = forRequest(mockPicasso(new FileRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(FileRequestHandler.class);
  }

  @Test public void forCustomRequest() {
    Action action = mockAction(CUSTOM_URI_KEY, CUSTOM_URI);
    BitmapHunter hunter = forRequest(mockPicasso(new CustomRequestHandler()), dispatcher, cache,
        stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(CustomRequestHandler.class);
  }

  @Test public void forOverrideRequest() {
    Action action = mockAction(ASSET_KEY_1, ASSET_URI_1);
    RequestHandler handler = new AssetRequestHandler(context);
    List<RequestHandler> handlers = Arrays.asList(handler);
    // Must use non-mock constructor because that is where Picasso's list of handlers is created.
    Picasso picasso = new Picasso(context, dispatcher, cache, null, null, handlers, stats,
        false, false);
    BitmapHunter hunter = forRequest(picasso, dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isEqualTo(handler);
  }

  @Test public void sequenceIsIncremented() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    Picasso picasso = mockPicasso();
    BitmapHunter hunter1 = forRequest(picasso, dispatcher, cache, stats, action);
    BitmapHunter hunter2 = forRequest(picasso, dispatcher, cache, stats, action);
    assertThat(hunter2.sequence).isGreaterThan(hunter1.sequence);
  }

  @Test public void getPriorityWithNoRequests() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action);
    hunter.detach(action);
    assertThat(hunter.getAction()).isNull();
    assertThat(hunter.getActions()).isNull();
    assertThat(hunter.getPriority()).isEqualTo(LOW);
  }

  @Test public void getPriorityWithSingleRequest() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1, HIGH);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action);
    assertThat(hunter.getAction()).isEqualTo(action);
    assertThat(hunter.getActions()).isNull();
    assertThat(hunter.getPriority()).isEqualTo(HIGH);
  }

  @Test public void getPriorityWithMultipleRequests() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1, NORMAL);
    Action action2 = mockAction(URI_KEY_1, URI_1, HIGH);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action1);
    hunter.attach(action2);
    assertThat(hunter.getAction()).isEqualTo(action1);
    assertThat(hunter.getActions()).hasSize(1).contains(action2);
    assertThat(hunter.getPriority()).isEqualTo(HIGH);
  }

  @Test public void getPriorityAfterDetach() throws Exception {
    Action action1 = mockAction(URI_KEY_1, URI_1, NORMAL);
    Action action2 = mockAction(URI_KEY_1, URI_1, HIGH);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action1);
    hunter.attach(action2);
    assertThat(hunter.getAction()).isEqualTo(action1);
    assertThat(hunter.getActions()).hasSize(1).contains(action2);
    assertThat(hunter.getPriority()).isEqualTo(HIGH);
    hunter.detach(action2);
    assertThat(hunter.getAction()).isEqualTo(action1);
    assertThat(hunter.getActions()).isEmpty();
    assertThat(hunter.getPriority()).isEqualTo(NORMAL);
  }

  @Test public void exifRotation() throws Exception {
    Request data = new Request.Builder(URI_1).rotate(-45).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, 90);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
  }

  @Test public void keepsAspectRationWhileResizingWhenDesiredWidthIs0() throws Exception {
    Request request = new Request.Builder(URI_1).resize(20, 0).build();
    Bitmap source = Bitmap.createBitmap(40, 20, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void keepsAspectRationWhileResizingWhenDesiredHeighIs0() throws Exception {
    Request request = new Request.Builder(URI_1).resize(0, 10).build();
    Bitmap source = Bitmap.createBitmap(40, 20, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void exifRotationWithManualRotation() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).rotate(-45).build();

    Bitmap result = transformResult(data, source, 90);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void rotation() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).rotate(-45).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void pivotRotation() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).rotate(-45, 10, 10).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0 10.0 10.0"));
  }

  @Test public void resize() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(20, 15).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.0 1.5");
  }

  @Test public void centerCropTallTooSmall() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(40, 40).centerCrop().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(5);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 4.0 4.0");
  }

  @Test public void centerCropTallTooLarge() throws Exception {
    Bitmap source = Bitmap.createBitmap(100, 200, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerCrop().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(50);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(100);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(100);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerCropWideTooSmall() throws Exception {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(40, 40).centerCrop().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(5);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 4.0 4.0");
  }

  @Test public void centerCropWideTooLarge() throws Exception {
    Bitmap source = Bitmap.createBitmap(200, 100, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerCrop().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(50);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(100);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(100);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerInsideTallTooSmall() throws Exception {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideTallTooLarge() throws Exception {
    Bitmap source = Bitmap.createBitmap(100, 50, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerInsideWideTooSmall() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideWideTooLarge() throws Exception {
    Bitmap source = Bitmap.createBitmap(50, 100, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);

    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void reusedBitmapIsNotRecycled() throws Exception {
    Request data = new Request.Builder(URI_1).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, 0);
    assertThat(result).isSameAs(source).isNotRecycled();
  }

  @Test public void crashingOnTransformationThrows() throws Exception {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        throw new NullPointerException("hello");
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Arrays.asList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessage("Transformation " + badTransformation.key() + " crashed with exception.");
    }
  }

  @Test public void nullResultFromTransformationThrows() throws Exception {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        return null;
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Arrays.asList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessageContaining(
          "Transformation " + badTransformation.key() + " returned null");
    }
  }

  @Test public void doesNotRecycleOriginalTransformationThrows() throws Exception {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        // Should recycle source.
        return Bitmap.createBitmap(10, 10, ARGB_8888);
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Arrays.asList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessage("Transformation "
          + badTransformation.key()
          + " mutated input Bitmap but failed to recycle the original.");
    }
  }

  @Test public void recycledOriginalTransformationThrows() throws Exception {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        source.recycle();
        return source;
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Arrays.asList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessage("Transformation "
          + badTransformation.key()
          + " returned input Bitmap but recycled it.");
    }
  }

  private static class TestableBitmapHunter extends BitmapHunter {
    TestableBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
        Action action) {
      this(picasso, dispatcher, cache, stats, action, null);
    }

    TestableBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
        Action action, Bitmap result) {
      this(picasso, dispatcher, cache, stats, action, result, null);
    }

    TestableBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
        Action action, Bitmap result, IOException exception) {
      super(picasso, dispatcher, cache, stats, action, spy(new TestableRequestHandler(result, exception)));
    }

    @Override Picasso.LoadedFrom getLoadedFrom() {
      return MEMORY;
    }
  }

  private static class TestableRequestHandler extends RequestHandler {
    private final Bitmap bitmap;
    private final IOException exception;

    TestableRequestHandler(Bitmap bitmap, IOException exception) {
      this.bitmap = bitmap;
      this.exception = exception;
    }

    @Override public boolean canHandleRequest(Request data) {
      return true;
    }

    @Override public Result load(Request data) throws IOException {
      if (exception != null) {
        throw exception;
      }
      return new Result(bitmap, MEMORY);
    }
  }

  private static class OOMBitmapHunter extends BitmapHunter {
    OOMBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
        Action action) {
      super(picasso, dispatcher, cache, stats, action, spy(new OOMRequestHandler()));
    }
  }

  private static class OOMRequestHandler extends TestableRequestHandler {
    OOMRequestHandler() {
      super(null, null);
    }

    @Override public Result load(Request data) throws IOException {
      throw new OutOfMemoryError();
    }
  }

  private static class CustomRequestHandler extends RequestHandler {
    @Override public boolean canHandleRequest(Request data) {
        return CUSTOM_URI.getScheme().equals(data.uri.getScheme());
    }

    @Override public Result load(Request data) {
      return new Result(BITMAP_1, MEMORY);
    }
  }
}
