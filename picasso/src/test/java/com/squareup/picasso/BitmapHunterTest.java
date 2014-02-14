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
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBitmap;
import org.robolectric.shadows.ShadowMatrix;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.FutureTask;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Bitmap.Config.RGB_565;
import static com.squareup.picasso.BitmapHunter.createBitmapOptions;
import static com.squareup.picasso.BitmapHunter.forRequest;
import static com.squareup.picasso.BitmapHunter.requiresInSampleSize;
import static com.squareup.picasso.BitmapHunter.transformResult;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.TestUtils.ASSET_KEY_1;
import static com.squareup.picasso.TestUtils.ASSET_URI_1;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.CONTACT_KEY_1;
import static com.squareup.picasso.TestUtils.CONTACT_URI_1;
import static com.squareup.picasso.TestUtils.CONTENT_1_URL;
import static com.squareup.picasso.TestUtils.CONTENT_KEY_1;
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
import static org.fest.assertions.api.ANDROID.assertThat;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.entry;
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
    BitmapHunter hunter =
        new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, BITMAP_1);
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
    BitmapHunter hunter =
        spy(new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, BITMAP_1));
    Bitmap result = hunter.hunt();
    verify(cache).get(URI_KEY_1);
    verify(hunter).decode(action.getData());
    assertThat(result).isEqualTo(BITMAP_1);
  }

  @Test public void huntReturnsWhenResultInCache() throws Exception {
    when(cache.get(URI_KEY_1)).thenReturn(BITMAP_1);
    Action action = mockAction(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter =
        spy(new TestableBitmapHunter(picasso, dispatcher, cache, stats, action, BITMAP_1));
    Bitmap result = hunter.hunt();
    verify(cache).get(URI_KEY_1);
    verify(hunter, never()).decode(action.getData());
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
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(ContentStreamBitmapHunter.class);
  }

  @Test public void forMediaStoreRequest() throws Exception {
    Action action = mockAction(MEDIA_STORE_CONTENT_KEY_1, MEDIA_STORE_CONTENT_1_URL);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(MediaStoreBitmapHunter.class);
  }

  @Test public void forContactsPhotoRequest() throws Exception {
    Action action = mockAction(CONTACT_KEY_1, CONTACT_URI_1);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(ContactsPhotoBitmapHunter.class);
  }

  @Test public void forNetworkRequest() throws Exception {
    Action action = mockAction(URI_KEY_1, URI_1);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(NetworkBitmapHunter.class);
  }

  @Test public void forFileWithAuthorityRequest() throws Exception {
    Action action = mockAction(FILE_KEY_1, FILE_1_URL);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(FileBitmapHunter.class);
  }

  @Test public void forAndroidResourceRequest() throws Exception {
    Action action = mockAction(RESOURCE_ID_KEY_1, null, null, RESOURCE_ID_1);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(ResourceBitmapHunter.class);
  }

  @Test public void forAndroidResourceUriWithId() throws Exception {
    Action action = mockAction(RESOURCE_ID_URI_KEY, RESOURCE_ID_URI);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(ResourceBitmapHunter.class);
  }

  @Test public void forAndroidResourceUriWithType() throws Exception {
    Action action = mockAction(RESOURCE_TYPE_URI_KEY, RESOURCE_TYPE_URI);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(ResourceBitmapHunter.class);
  }

  @Test public void forAssetRequest() {
    Action action = mockAction(ASSET_KEY_1, ASSET_URI_1);
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(AssetBitmapHunter.class);
  }

  @Test public void forFileWithNoPathSegments() {
    Action action = mockAction("keykeykey", Uri.fromFile(new File("/")));
    BitmapHunter hunter =
        forRequest(context, picasso, dispatcher, cache, stats, action, downloader);
    assertThat(hunter).isInstanceOf(FileBitmapHunter.class);
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

  @Test public void bitmapConfig() throws Exception {
    for (Bitmap.Config config : Bitmap.Config.values()) {
      Request data = new Request.Builder(URI_1).config(config).build();
      Request copy = data.buildUpon().build();

      assertThat(createBitmapOptions(data).inPreferredConfig).isSameAs(config);
      assertThat(createBitmapOptions(copy).inPreferredConfig).isSameAs(config);
    }
  }

  @Test public void requiresComputeInSampleSize() {
    assertThat(requiresInSampleSize(null)).isFalse();
    final BitmapFactory.Options defaultOptions = new BitmapFactory.Options();
    assertThat(requiresInSampleSize(defaultOptions)).isFalse();
    final BitmapFactory.Options justBounds = new BitmapFactory.Options();
    justBounds.inJustDecodeBounds = true;
    assertThat(requiresInSampleSize(justBounds)).isTrue();
  }

  @Test public void nullBitmapOptionsIfNoResizing() {
    // No resize must return no bitmap options
    final Request noResize = new Request.Builder(URI_1).build();
    final BitmapFactory.Options noResizeOptions = createBitmapOptions(noResize);
    assertThat(noResizeOptions).isNull();
  }

  @Test public void inJustDecodeBoundsIfResizing() {
    // Resize must return bitmap options with inJustDecodeBounds = true
    final Request requiresResize = new Request.Builder(URI_1).resize(20, 15).build();
    final BitmapFactory.Options resizeOptions = createBitmapOptions(requiresResize);
    assertThat(resizeOptions).isNotNull();
    assertThat(resizeOptions.inJustDecodeBounds).isTrue();
  }

  @Test public void createWithConfigAndNotInJustDecodeBounds() {
    // Given a config must return bitmap options and false inJustDecodeBounds
    final Request config = new Request.Builder(URI_1).config(RGB_565).build();
    final BitmapFactory.Options configOptions = createBitmapOptions(config);
    assertThat(configOptions).isNotNull();
    assertThat(configOptions.inJustDecodeBounds).isFalse();
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

  @Test public void exif90SwapsDimensions() throws Exception {
    Request data = new Request.Builder(URI_1).build();
    Bitmap in = Bitmap.createBitmap(30, 40, null);
    Bitmap out = transformResult(data, in, 90);
    assertThat(out).hasWidth(40).hasHeight(30);
  }

  @Test public void exif270SwapsDimensions() throws Exception {
    Request data = new Request.Builder(URI_1).build();
    Bitmap in = Bitmap.createBitmap(30, 40, null);
    Bitmap out = transformResult(data, in, 270);
    assertThat(out).hasWidth(40).hasHeight(30);
  }

  @Test public void reusedBitmapIsNotRecycled() throws Exception {
    Request data = new Request.Builder(URI_1).build();
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(data, source, 0);
    assertThat(result).isSameAs(source).isNotRecycled();
  }

  private static class TestableBitmapHunter extends BitmapHunter {
    private final Bitmap result;
    private final IOException exception;

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
      super(picasso, dispatcher, cache, stats, action);
      this.result = result;
      this.exception = exception;
    }

    @Override Bitmap decode(Request data) throws IOException {
      if (exception != null) {
        throw exception;
      }
      return result;
    }

    @Override Picasso.LoadedFrom getLoadedFrom() {
      return MEMORY;
    }
  }

  private static class OOMBitmapHunter extends TestableBitmapHunter {

    OOMBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
        Action action) {
      super(picasso, dispatcher, cache, stats, action);
    }

    @Override Bitmap decode(Request data) throws IOException {
      throw new OutOfMemoryError();
    }
  }
}
