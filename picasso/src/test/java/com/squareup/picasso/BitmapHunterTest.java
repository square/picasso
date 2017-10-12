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
import android.view.Gravity;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.shadows.ShadowBitmap;
import org.robolectric.shadows.ShadowMatrix;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
import static android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL;
import static android.media.ExifInterface.ORIENTATION_ROTATE_90;
import static android.media.ExifInterface.ORIENTATION_TRANSPOSE;
import static android.media.ExifInterface.ORIENTATION_TRANSVERSE;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso.BitmapHunter.forRequest;
import static com.squareup.picasso.BitmapHunter.transformResult;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.Picasso.Priority.HIGH;
import static com.squareup.picasso.Picasso.Priority.LOW;
import static com.squareup.picasso.Picasso.Priority.NORMAL;
import static com.squareup.picasso.TestUtils.ASSET_KEY_1;
import static com.squareup.picasso.TestUtils.ASSET_URI_1;
import static com.squareup.picasso.TestUtils.CONTACT_KEY_1;
import static com.squareup.picasso.TestUtils.CONTACT_PHOTO_KEY_1;
import static com.squareup.picasso.TestUtils.CONTACT_PHOTO_URI_1;
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
import static com.squareup.picasso.TestUtils.makeBitmap;
import static com.squareup.picasso.TestUtils.mockAction;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockPicasso;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricGradleTestRunner.class)
public class BitmapHunterTest {

  @Mock Context context;
  @Mock Picasso picasso;
  @Mock Cache cache;
  @Mock Stats stats;
  @Mock Dispatcher dispatcher;
  @Mock Downloader downloader;

  final Bitmap bitmap = makeBitmap();

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void nullDecodeResponseIsError() {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, null);
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void runWithResultDispatchComplete() {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, bitmap);
    hunter.run();
    verify(dispatcher).dispatchComplete(hunter);
  }

  @Test public void runWithNoResultDispatchFailed() {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action);
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void responseExceptionDispatchFailed() {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, null,
        new NetworkRequestHandler.ResponseException(0, 504));
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void outOfMemoryDispatchFailed() {
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

  @Test public void runWithIoExceptionDispatchRetry() {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, null,
        new IOException());
    hunter.run();
    verify(dispatcher).dispatchRetry(hunter);
  }

  @Test public void huntDecodesWhenNotInCache() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    TestableBitmapHunter hunter =
        new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, bitmap);

    Bitmap result = hunter.hunt();
    verify(cache).get(URI_KEY_1);
    verify(hunter.requestHandler).load(action.getRequest(), 0);
    assertThat(result).isEqualTo(bitmap);
  }

  @Test public void huntReturnsWhenResultInCache() throws Exception {
    when(cache.get(URI_KEY_1)).thenReturn(bitmap);
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    TestableBitmapHunter hunter =
        new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, bitmap);

    Bitmap result = hunter.hunt();
    verify(cache).get(URI_KEY_1);
    verify(hunter.requestHandler, never()).load(action.getRequest(), 0);
    assertThat(result).isEqualTo(bitmap);
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
    assertThat(result).isEqualTo(bitmap);
  }

  @Test public void attachSingleRequest() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    assertThat(hunter.action).isEqualTo(action1);
    hunter.detach(action1);
    hunter.attach(action1);
    assertThat(hunter.action).isEqualTo(action1);
    assertThat(hunter.actions).isNull();
  }

  @Test public void attachMultipleRequests() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    assertThat(hunter.actions).isNull();
    hunter.attach(action2);
    assertThat(hunter.actions).isNotNull();
    assertThat(hunter.actions).hasSize(1);
  }

  @Test public void detachSingleRequest() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action);
    assertThat(hunter.action).isNotNull();
    hunter.detach(action);
    assertThat(hunter.action).isNull();
  }

  @Test public void detachMultipleRequests() {
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action);
    hunter.attach(action2);
    hunter.detach(action2);
    assertThat(hunter.action).isNotNull();
    assertThat(hunter.actions).isNotNull();
    assertThat(hunter.actions).isEmpty();
    hunter.detach(action);
    assertThat(hunter.action).isNull();
  }

  @Test public void cancelSingleRequest() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    assertThat(hunter.isCancelled()).isFalse();
    assertThat(hunter.cancel()).isFalse();
    hunter.detach(action1);
    assertThat(hunter.cancel()).isTrue();
    assertThat(hunter.isCancelled()).isTrue();
  }

  @Test public void cancelMultipleRequests() {
    Action action1 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, cache, stats, action1);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.attach(action2);
    assertThat(hunter.isCancelled()).isFalse();
    assertThat(hunter.cancel()).isFalse();
    hunter.detach(action1);
    hunter.detach(action2);
    assertThat(hunter.cancel()).isTrue();
    assertThat(hunter.isCancelled()).isTrue();
  }

  // ---------------------------------------

  @Test public void forContentProviderRequest() {
    Action action = mockAction(CONTENT_KEY_1, CONTENT_1_URL);
    BitmapHunter hunter = forRequest(mockPicasso(new ContentStreamRequestHandler(context)),
        dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ContentStreamRequestHandler.class);
  }

  @Test public void forMediaStoreRequest() {
    Action action = mockAction(MEDIA_STORE_CONTENT_KEY_1, MEDIA_STORE_CONTENT_1_URL);
    BitmapHunter hunter = forRequest(mockPicasso(new MediaStoreRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(MediaStoreRequestHandler.class);
  }

  @Test public void forContactsPhotoRequest() {
    Action action = mockAction(CONTACT_KEY_1, CONTACT_URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new ContactsPhotoRequestHandler(context)),
        dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ContactsPhotoRequestHandler.class);
  }

  @Test public void forContactsThumbnailPhotoRequest() {
    Action action = mockAction(CONTACT_PHOTO_KEY_1, CONTACT_PHOTO_URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new ContactsPhotoRequestHandler(context)),
      dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ContactsPhotoRequestHandler.class);
  }

  @Test public void forNetworkRequest() {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(NetworkRequestHandler.class);
  }

  @Test public void forFileWithAuthorityRequest() {
    Action action = mockAction(FILE_KEY_1, FILE_1_URL);
    BitmapHunter hunter = forRequest(mockPicasso(new FileRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(FileRequestHandler.class);
  }

  @Test public void forAndroidResourceRequest() {
    Action action = mockAction(RESOURCE_ID_KEY_1, null, null, RESOURCE_ID_1);
    BitmapHunter hunter = forRequest(mockPicasso(new ResourceRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler.class);
  }

  @Test public void forAndroidResourceUriWithId() {
    Action action = mockAction(RESOURCE_ID_URI_KEY, RESOURCE_ID_URI);
    BitmapHunter hunter = forRequest(mockPicasso(new ResourceRequestHandler(context)), dispatcher,
        cache, stats, action);
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler.class);
  }

  @Test public void forAndroidResourceUriWithType() {
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
    List<RequestHandler> handlers = Collections.singletonList(handler);
    // Must use non-mock constructor because that is where Picasso's list of handlers is created.
    Picasso picasso = new Picasso(context, dispatcher, cache, null, null, handlers, stats,
        ARGB_8888, false, false);
    BitmapHunter hunter = forRequest(picasso, dispatcher, cache, stats, action);
    assertThat(hunter.requestHandler).isEqualTo(handler);
  }

  @Test public void sequenceIsIncremented() {
    Action action = mockAction(URI_KEY_1, URI_1);
    Picasso picasso = mockPicasso();
    BitmapHunter hunter1 = forRequest(picasso, dispatcher, cache, stats, action);
    BitmapHunter hunter2 = forRequest(picasso, dispatcher, cache, stats, action);
    assertThat(hunter2.sequence).isGreaterThan(hunter1.sequence);
  }

  @Test public void getPriorityWithNoRequests() {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action);
    hunter.detach(action);
    assertThat(hunter.getAction()).isNull();
    assertThat(hunter.getActions()).isNull();
    assertThat(hunter.getPriority()).isEqualTo(LOW);
  }

  @Test public void getPriorityWithSingleRequest() {
    Action action = mockAction(URI_KEY_1, URI_1, HIGH);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action);
    assertThat(hunter.getAction()).isEqualTo(action);
    assertThat(hunter.getActions()).isNull();
    assertThat(hunter.getPriority()).isEqualTo(HIGH);
  }

  @Test public void getPriorityWithMultipleRequests() {
    Action action1 = mockAction(URI_KEY_1, URI_1, NORMAL);
    Action action2 = mockAction(URI_KEY_1, URI_1, HIGH);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action1);
    hunter.attach(action2);
    assertThat(hunter.getAction()).isEqualTo(action1);
    assertThat(hunter.getActions()).containsExactly(action2);
    assertThat(hunter.getPriority()).isEqualTo(HIGH);
  }

  @Test public void getPriorityAfterDetach() {
    Action action1 = mockAction(URI_KEY_1, URI_1, NORMAL);
    Action action2 = mockAction(URI_KEY_1, URI_1, HIGH);
    BitmapHunter hunter = forRequest(mockPicasso(new NetworkRequestHandler(downloader, stats)),
        dispatcher, cache, stats, action1);
    hunter.attach(action2);
    assertThat(hunter.getAction()).isEqualTo(action1);
    assertThat(hunter.getActions()).containsExactly(action2);
    assertThat(hunter.getPriority()).isEqualTo(HIGH);
    hunter.detach(action2);
    assertThat(hunter.getAction()).isEqualTo(action1);
    assertThat(hunter.getActions()).isEmpty();
    assertThat(hunter.getPriority()).isEqualTo(NORMAL);
  }

  @Test public void exifRotation() {
    Request data = new Request.Builder(URI_1).rotate(-45).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, ORIENTATION_ROTATE_90);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("rotate 90.0");
  }

 @Test public void exifRotationSizing() throws Exception {
    Request data = new Request.Builder(URI_1).resize(5, 10).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, ORIENTATION_ROTATE_90);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).contains("scale 1.0 0.5");
  }

 @Test public void exifRotationNoSizing() throws Exception {
    Request data = new Request.Builder(URI_1).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, ORIENTATION_ROTATE_90);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).contains("rotate 90.0");
  }

 @Test public void rotation90Sizing() throws Exception {
    Request data = new Request.Builder(URI_1).rotate(90).resize(5, 10).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, 0);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).contains("scale 1.0 0.5");
  }

 @Test public void rotation180Sizing() throws Exception {
    Request data = new Request.Builder(URI_1).rotate(180).resize(5, 10).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, 0);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).contains("scale 0.5 1.0");
  }

 @Test public void rotation90WithPivotSizing() throws Exception {
    Request data = new Request.Builder(URI_1).rotate(90,0,10).resize(5, 10).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, 0);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).contains("scale 1.0 0.5");
  }

  @Test public void exifVerticalFlip() {
    Request data = new Request.Builder(URI_1).rotate(-45).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, ORIENTATION_FLIP_VERTICAL);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPostOperations()).containsExactly("scale -1.0 1.0");
    assertThat(shadowMatrix.getPreOperations()).containsExactly("rotate 180.0");
  }

  @Test public void exifHorizontalFlip() {
    Request data = new Request.Builder(URI_1).rotate(-45).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, ORIENTATION_FLIP_HORIZONTAL);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPostOperations()).containsExactly("scale -1.0 1.0");
    assertThat(shadowMatrix.getPreOperations()).doesNotContain("rotate 180.0");
    assertThat(shadowMatrix.getPreOperations()).doesNotContain("rotate 90.0");
    assertThat(shadowMatrix.getPreOperations()).doesNotContain("rotate 270.0");
  }

  @Test public void exifTranspose() {
    Request data = new Request.Builder(URI_1).rotate(-45).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, ORIENTATION_TRANSPOSE);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPostOperations()).containsExactly("scale -1.0 1.0");
    assertThat(shadowMatrix.getPreOperations()).containsExactly("rotate 90.0");
  }

  @Test public void exifTransverse() {
    Request data = new Request.Builder(URI_1).rotate(-45).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, ORIENTATION_TRANSVERSE);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPostOperations()).containsExactly("scale -1.0 1.0");
    assertThat(shadowMatrix.getPreOperations()).containsExactly("rotate 270.0");
  }

  @Test public void keepsAspectRationWhileResizingWhenDesiredWidthIs0() {
    Request request = new Request.Builder(URI_1).resize(20, 0).build();
    Bitmap source = Bitmap.createBitmap(40, 20, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.5 0.5");
  }

  @Test public void keepsAspectRationWhileResizingWhenDesiredHeightIs0() {
    Request request = new Request.Builder(URI_1).resize(0, 10).build();
    Bitmap source = Bitmap.createBitmap(40, 20, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.5 0.5");
  }

  @Test public void centerCropResultMatchesTargetSize() {
    Request request = new Request.Builder(URI_1).resize(1080, 642).centerCrop().build();
    Bitmap source = Bitmap.createBitmap(640, 640, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    String scalePreOperation = shadowMatrix.getPreOperations().get(0);

    assertThat(scalePreOperation).startsWith("scale ");
    float scaleX = Float.valueOf(scalePreOperation.split(" ")[1]);
    float scaleY = Float.valueOf(scalePreOperation.split(" ")[2]);

    int transformedWidth = Math.round(result.getWidth() * scaleX);
    int transformedHeight = Math.round(result.getHeight() * scaleY);
    assertThat(transformedWidth).isEqualTo(1080);
    assertThat(transformedHeight).isEqualTo(642);
  }

  @Test public void centerCropResultMatchesTargetSizeWhileDesiredWidthIs0() {
    Request request = new Request.Builder(URI_1).resize(0, 642).centerCrop().build();
    Bitmap source = Bitmap.createBitmap(640, 640, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    String scalePreOperation = shadowMatrix.getPreOperations().get(0);

    assertThat(scalePreOperation).startsWith("scale ");
    float scaleX = Float.valueOf(scalePreOperation.split(" ")[1]);
    float scaleY = Float.valueOf(scalePreOperation.split(" ")[2]);

    int transformedWidth = Math.round(result.getWidth() * scaleX);
    int transformedHeight = Math.round(result.getHeight() * scaleY);
    assertThat(transformedWidth).isEqualTo(642);
    assertThat(transformedHeight).isEqualTo(642);
  }

  @Test public void centerCropResultMatchesTargetSizeWhileDesiredHeightIs0() {
    Request request = new Request.Builder(URI_1).resize(1080, 0).centerCrop().build();
    Bitmap source = Bitmap.createBitmap(640, 640, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    String scalePreOperation = shadowMatrix.getPreOperations().get(0);

    assertThat(scalePreOperation).startsWith("scale ");
    float scaleX = Float.valueOf(scalePreOperation.split(" ")[1]);
    float scaleY = Float.valueOf(scalePreOperation.split(" ")[2]);

    int transformedWidth = Math.round(result.getWidth() * scaleX);
    int transformedHeight = Math.round(result.getHeight() * scaleY);
    assertThat(transformedWidth).isEqualTo(1080);
    assertThat(transformedHeight).isEqualTo(1080);
  }

  @Test public void centerInsideResultMatchesTargetSizeWhileDesiredWidthIs0() {
    Request request = new Request.Builder(URI_1).resize(0, 642).centerInside().build();
    Bitmap source = Bitmap.createBitmap(640, 640, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    String scalePreOperation = shadowMatrix.getPreOperations().get(0);

    assertThat(scalePreOperation).startsWith("scale ");
    float scaleX = Float.valueOf(scalePreOperation.split(" ")[1]);
    float scaleY = Float.valueOf(scalePreOperation.split(" ")[2]);

    int transformedWidth = Math.round(result.getWidth() * scaleX);
    int transformedHeight = Math.round(result.getHeight() * scaleY);
    assertThat(transformedWidth).isEqualTo(642);
    assertThat(transformedHeight).isEqualTo(642);
  }

  @Test public void centerInsideResultMatchesTargetSizeWhileDesiredHeightIs0() {
    Request request = new Request.Builder(URI_1).resize(1080, 0).centerInside().build();
    Bitmap source = Bitmap.createBitmap(640, 640, ARGB_8888);

    Bitmap result = transformResult(request, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    String scalePreOperation = shadowMatrix.getPreOperations().get(0);

    assertThat(scalePreOperation).startsWith("scale ");
    float scaleX = Float.valueOf(scalePreOperation.split(" ")[1]);
    float scaleY = Float.valueOf(scalePreOperation.split(" ")[2]);

    int transformedWidth = Math.round(result.getWidth() * scaleX);
    int transformedHeight = Math.round(result.getHeight() * scaleY);
    assertThat(transformedWidth).isEqualTo(1080);
    assertThat(transformedHeight).isEqualTo(1080);
  }

  @Test public void exifRotationWithManualRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).rotate(-45).build();

    Bitmap result = transformResult(data, source, ORIENTATION_ROTATE_90);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("rotate 90.0");
    assertThat(shadowMatrix.getSetOperations()).containsEntry("rotate", "-45.0");
  }

  @Test public void rotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).rotate(-45).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).containsEntry("rotate", "-45.0");
  }

  @Test public void pivotRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).rotate(-45, 10, 10).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).containsEntry("rotate", "-45.0 10.0 10.0");
  }

  @Test public void resize() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(20, 15).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 2.0 1.5");
  }

  @Test public void centerCropTallTooSmall() {
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
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 4.0 4.0");
  }

  @Test public void centerCropTallTooLarge() {
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
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.5 0.5");
  }

  @Test public void centerCropWideTooSmall() {
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
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 4.0 4.0");
  }

  @Test public void centerCropWithGravityHorizontalLeft() {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.LEFT).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 4.0 4.0");
  }

  @Test public void centerCropWithGravityHorizontalRight() {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.RIGHT).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 4.0 4.0");
  }

  @Test public void centerCropWithGravityVerticalTop() {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.TOP).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 4.0 4.0");
  }

  @Test public void centerCropWithGravityVerticalBottom() {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.BOTTOM).build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 4.0 4.0");
  }

  @Test public void centerCropWideTooLarge() {
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
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.5 0.5");
  }

  @Test public void centerInsideTallTooSmall() {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 2.5 2.5");
  }

  @Test public void centerInsideTallTooLarge() {
    Bitmap source = Bitmap.createBitmap(100, 50, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.5 0.5");
  }

  @Test public void centerInsideWideTooSmall() {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 2.5 2.5");
  }

  @Test public void centerInsideWideTooLarge() {
    Bitmap source = Bitmap.createBitmap(50, 100, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).centerInside().build();

    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);

    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.5 0.5");
  }

  @Test public void onlyScaleDownOriginalBigger() {
    Bitmap source = Bitmap.createBitmap(100, 100, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(50, 50).onlyScaleDown().build();
    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);

    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.5 0.5");
  }

  @Test public void onlyScaleDownOriginalSmaller() {
    Bitmap source = Bitmap.createBitmap(50, 50, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(100, 100).onlyScaleDown().build();
    Bitmap result = transformResult(data, source, 0);
    assertThat(result).isSameAs(source);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isNull();
    assertThat(shadowBitmap.getCreatedFromBitmap()).isNotSameAs(source);
  }

  @Test public void onlyScaleDownOriginalSmallerWidthIs0() {
    Bitmap source = Bitmap.createBitmap(50, 50, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(0, 60).onlyScaleDown().build();
    Bitmap result = transformResult(data, source, 0);
    assertThat(result).isSameAs(source);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isNull();
  }

  @Test public void onlyScaleDownOriginalSmallerHeightIs0() {
    Bitmap source = Bitmap.createBitmap(50, 50, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(60, 0).onlyScaleDown().build();
    Bitmap result = transformResult(data, source, 0);
    assertThat(result).isSameAs(source);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isNull();
  }

  @Test public void onlyScaleDownOriginalBiggerWidthIs0() {
    Bitmap source = Bitmap.createBitmap(50, 50, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(0, 40).onlyScaleDown().build();
    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);

    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.8 0.8");
  }

  @Test public void onlyScaleDownOriginalBiggerHeightIs0() {
    Bitmap source = Bitmap.createBitmap(50, 50, ARGB_8888);
    Request data = new Request.Builder(URI_1).resize(40, 0).onlyScaleDown().build();
    Bitmap result = transformResult(data, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);

    assertThat(shadowMatrix.getPreOperations()).containsExactly("scale 0.8 0.8");
  }

  @Test public void reusedBitmapIsNotRecycled() {
    Request data = new Request.Builder(URI_1).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, 0);
    assertThat(result).isSameAs(source);
    assertThat(result.isRecycled()).isFalse();
  }

  @Test public void crashingOnTransformationThrows() {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        throw new NullPointerException("hello");
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Collections.singletonList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().isEqualTo("Transformation " + badTransformation.key() + " crashed with exception.");
    }
  }

  @Test public void nullResultFromTransformationThrows() {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        return null;
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Collections.singletonList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains(
          "Transformation " + badTransformation.key() + " returned null");
    }
  }

  @Test public void doesNotRecycleOriginalTransformationThrows() {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        // Should recycle source.
        return Bitmap.createBitmap(10, 10, ARGB_8888);
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Collections.singletonList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().isEqualTo("Transformation "
          + badTransformation.key()
          + " mutated input Bitmap but failed to recycle the original.");
    }
  }

  @Test public void recycledOriginalTransformationThrows() {
    Transformation badTransformation = new Transformation() {
      @Override public Bitmap transform(Bitmap source) {
        source.recycle();
        return source;
      }

      @Override public String key() {
        return "test";
      }
    };
    List<Transformation> transformations = Collections.singletonList(badTransformation);
    Bitmap original = Bitmap.createBitmap(10, 10, ARGB_8888);
    try {
      BitmapHunter.applyCustomTransformations(transformations, original);
      fail("Expected exception to be thrown.");
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().isEqualTo("Transformation "
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

    @Override public Result load(Request request, int networkPolicy) throws IOException {
      if (exception != null) {
        throw exception;
      }
      return new Result(bitmap, MEMORY);
    }

    @Override int getRetryCount() {
      return 1;
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

    @Override public Result load(Request request, int networkPolicy) throws IOException {
      throw new OutOfMemoryError();
    }
  }

  private class CustomRequestHandler extends RequestHandler {
    @Override public boolean canHandleRequest(Request data) {
        return CUSTOM_URI.getScheme().equals(data.uri.getScheme());
    }

    @Override public Result load(Request request, int networkPolicy) {
      return new Result(bitmap, MEMORY);
    }
  }
}
