package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import java.io.IOException;
import java.util.concurrent.FutureTask;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowBitmap;
import org.robolectric.shadows.ShadowMatrix;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.squareup.picasso.BitmapHunter.forRequest;
import static com.squareup.picasso.BitmapHunter.transformResult;
import static com.squareup.picasso.Request.LoadedFrom;
import static com.squareup.picasso.Request.LoadedFrom.MEMORY;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.CONTACT_KEY_1;
import static com.squareup.picasso.TestUtils.CONTACT_URI_1;
import static com.squareup.picasso.TestUtils.CONTENT_1_URL;
import static com.squareup.picasso.TestUtils.CONTENT_KEY_1;
import static com.squareup.picasso.TestUtils.FILE_1_URL;
import static com.squareup.picasso.TestUtils.FILE_KEY_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_1;
import static com.squareup.picasso.TestUtils.RESOURCE_ID_KEY_1;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockImageViewTarget;
import static com.squareup.picasso.TestUtils.mockRequest;
import static org.fest.assertions.api.ANDROID.assertThat;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BitmapHunterTest {

  @Mock Context context;
  @Mock Picasso picasso;
  @Mock Dispatcher dispatcher;
  @Mock Downloader downloader;

  @Before public void setUp() throws Exception {
    initMocks(this);
  }

  @Test public void runWithResultDispatchComplete() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, request, BITMAP_1);
    hunter.run();
    verify(dispatcher).dispatchComplete(hunter);
  }

  @Test public void runWithNoResultDispatchFailed() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, request);
    hunter.run();
    verify(dispatcher).dispatchFailed(hunter);
  }

  @Test public void runWithIoExceptionDispatchRetry() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, request, null, true);
    hunter.run();
    verify(dispatcher).dispatchRetry(hunter);
  }

  @Test public void attachRequest() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, request1);
    hunter.attach(request1);
    assertThat(hunter.requests).hasSize(1);
    hunter.attach(request2);
    assertThat(hunter.requests).hasSize(2);
  }

  @Test public void detachRequest() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, request);
    hunter.attach(request);
    assertThat(hunter.requests).hasSize(1);
    hunter.detach(request);
    assertThat(hunter.requests).isEmpty();
  }

  @Test public void cancelRequest() throws Exception {
    Request request1 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    Request request2 = mockRequest(URI_KEY_1, URI_1, mockImageViewTarget());
    BitmapHunter hunter = new TestableBitmapHunter(picasso, dispatcher, request1);
    hunter.future = new FutureTask<Object>(mock(Runnable.class), mock(Object.class));
    hunter.attach(request1);
    hunter.attach(request2);
    assertThat(hunter.cancel()).isFalse();
    hunter.detach(request1);
    hunter.detach(request2);
    assertThat(hunter.cancel()).isTrue();
  }

  // ---------------------------------------

  @Test public void forContentProviderRequest() throws Exception {
    Request request = mockRequest(CONTENT_KEY_1, CONTENT_1_URL);
    BitmapHunter hunter = forRequest(context, picasso, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(ContentProviderBitmapHunter.class);
  }

  @Test public void forContactsPhotoRequest() throws Exception {
    Request request = mockRequest(CONTACT_KEY_1, CONTACT_URI_1);
    BitmapHunter hunter = forRequest(context, picasso, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(ContactsPhotoBitmapHunter.class);
  }

  @Test public void forNetworkRequest() throws Exception {
    Request request = mockRequest(URI_KEY_1, URI_1);
    BitmapHunter hunter = forRequest(context, picasso, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(NetworkBitmapHunter.class);
  }

  @Test public void forFileWithAuthorityRequest() throws Exception {
    Request request = mockRequest(FILE_KEY_1, FILE_1_URL);
    BitmapHunter hunter = forRequest(context, picasso, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(FileBitmapHunter.class);
  }

  @Test public void forAndroidResourceRequest() throws Exception {
    Request request = mockRequest(RESOURCE_ID_KEY_1, null, null, RESOURCE_ID_1);
    BitmapHunter hunter = forRequest(context, picasso, dispatcher, request, downloader);
    assertThat(hunter).isInstanceOf(ResourceBitmapHunter.class);
  }

  // TODO more static forTests

  @Test public void exifRotation() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(null, source, 90);
    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
  }

  @Test public void exifRotationWithManualRotation() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;

    Bitmap result = transformResult(options, source, 90);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void rotation() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void pivotRotation() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;
    options.targetPivotX = 10;
    options.targetPivotY = 10;
    options.hasRotationPivot = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0 10.0 10.0"));
  }

  @Test public void scale() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetScaleX = -0.5f;
    options.targetScaleY = 2;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("scale", "-0.5 2.0"));
  }

  @Test public void resize() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 20;
    options.targetHeight = 15;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.0 1.5");
  }

  @Test public void centerCropTallTooSmall() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 40;
    options.targetHeight = 40;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

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
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

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
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 40;
    options.targetHeight = 40;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

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
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerCrop = true;

    Bitmap result = transformResult(options, source, 0);

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
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideTallTooLarge() throws Exception {
    Bitmap source = Bitmap.createBitmap(100, 50, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerInsideWideTooSmall() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideWideTooLarge() throws Exception {
    Bitmap source = Bitmap.createBitmap(50, 100, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);

    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void reusedBitmapIsNotRecycled() throws Exception {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = transformResult(null, source, 0);
    assertThat(result).isSameAs(source).isNotRecycled();
  }

  private static class TestableBitmapHunter extends BitmapHunter {
    private final Bitmap result;
    private final boolean throwException;

    TestableBitmapHunter(Picasso picasso, Dispatcher dispatcher, Request request) {
      this(picasso, dispatcher, request, null);
    }

    TestableBitmapHunter(Picasso picasso, Dispatcher dispatcher, Request request, Bitmap result) {
      this(picasso, dispatcher, request, result, false);
    }

    TestableBitmapHunter(Picasso picasso, Dispatcher dispatcher, Request request, Bitmap result,
        boolean throwException) {
      super(picasso, dispatcher, request);
      this.result = result;
      this.throwException = throwException;
    }

    @Override Bitmap decode(Uri uri, PicassoBitmapOptions options) throws IOException {
      if (throwException) {
        throw new IOException("Failed.");
      }
      return result;
    }

    @Override LoadedFrom getLoadedFrom() {
      return MEMORY;
    }
  }
}
