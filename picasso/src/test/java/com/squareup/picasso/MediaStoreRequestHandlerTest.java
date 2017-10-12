package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBitmap;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.FULL;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.MICRO;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.MINI;
import static com.squareup.picasso.MediaStoreRequestHandler.getPicassoKind;
import static com.squareup.picasso.TestUtils.MEDIA_STORE_CONTENT_1_URL;
import static com.squareup.picasso.TestUtils.MEDIA_STORE_CONTENT_KEY_1;
import static com.squareup.picasso.TestUtils.makeBitmap;
import static com.squareup.picasso.TestUtils.mockAction;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricGradleTestRunner.class)
@Config(shadows = { Shadows.ShadowVideoThumbnails.class, Shadows.ShadowImageThumbnails.class })
public class MediaStoreRequestHandlerTest {

  @Mock Context context;

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void decodesVideoThumbnailWithVideoMimeType() throws Exception {
    Bitmap bitmap = makeBitmap();
    Request request =
        new Request.Builder(MEDIA_STORE_CONTENT_1_URL, 0, ARGB_8888).resize(100, 100).build();
    Action action = mockAction(MEDIA_STORE_CONTENT_KEY_1, request);
    MediaStoreRequestHandler requestHandler = create("video/");
    Bitmap result = requestHandler.load(action.getRequest(), 0).getBitmap();
    assertBitmapsEqual(result, bitmap);
  }

  @Test public void decodesImageThumbnailWithImageMimeType() throws Exception {
    Bitmap bitmap = makeBitmap(20, 20);
    Request request =
        new Request.Builder(MEDIA_STORE_CONTENT_1_URL, 0, ARGB_8888).resize(100, 100).build();
    Action action = mockAction(MEDIA_STORE_CONTENT_KEY_1, request);
    MediaStoreRequestHandler requestHandler = create("image/png");
    Bitmap result = requestHandler.load(action.getRequest(), 0).getBitmap();
    assertBitmapsEqual(result, bitmap);
  }

  @Test public void getPicassoKindMicro() throws Exception {
    assertThat(getPicassoKind(96, 96)).isEqualTo(MICRO);
    assertThat(getPicassoKind(95, 95)).isEqualTo(MICRO);
  }

  @Test public void getPicassoKindMini() throws Exception {
    assertThat(getPicassoKind(512, 384)).isEqualTo(MINI);
    assertThat(getPicassoKind(100, 100)).isEqualTo(MINI);
  }

  @Test public void getPicassoKindFull() throws Exception {
    assertThat(getPicassoKind(513, 385)).isEqualTo(FULL);
    assertThat(getPicassoKind(1000, 1000)).isEqualTo(FULL);
    assertThat(getPicassoKind(1000, 384)).isEqualTo(FULL);
    assertThat(getPicassoKind(1000, 96)).isEqualTo(FULL);
    assertThat(getPicassoKind(96, 1000)).isEqualTo(FULL);
  }

  private MediaStoreRequestHandler create(String mimeType) {
    ContentResolver contentResolver = mock(ContentResolver.class);
    when(contentResolver.getType(any(Uri.class))).thenReturn(mimeType);
    return create(contentResolver);
  }

  private MediaStoreRequestHandler create(ContentResolver contentResolver) {
    when(context.getContentResolver()).thenReturn(contentResolver);
    return new MediaStoreRequestHandler(context);
  }

  private static void assertBitmapsEqual(Bitmap a, Bitmap b) {
    ShadowBitmap shadowA = shadowOf(a);
    ShadowBitmap shadowB = shadowOf(b);

    if (shadowA.getHeight() != shadowB.getHeight()) {
      fail();
    }
    if (shadowA.getWidth() != shadowB.getWidth()) {
      fail();
    }
    if (shadowA.getDescription() != null ? !shadowA.getDescription().equals(shadowB.getDescription()) : shadowB.getDescription() != null) {
      fail();
    }
  }
}
