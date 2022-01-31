package com.squareup.picasso3

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.MediaStoreRequestHandler.Companion.getPicassoKind
import com.squareup.picasso3.MediaStoreRequestHandler.PicassoKind.FULL
import com.squareup.picasso3.MediaStoreRequestHandler.PicassoKind.MICRO
import com.squareup.picasso3.MediaStoreRequestHandler.PicassoKind.MINI
import com.squareup.picasso3.RequestHandler.Callback
import com.squareup.picasso3.Shadows.ShadowImageThumbnails
import com.squareup.picasso3.Shadows.ShadowVideoThumbnails
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_1_URL
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_KEY_1
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.mockAction
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations.initMocks
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowVideoThumbnails::class, ShadowImageThumbnails::class])
class MediaStoreRequestHandlerTest {
  @Mock lateinit var context: Context
  @Mock lateinit var picasso: Picasso

  @Before fun setUp() {
    initMocks(this)
  }

  @Test fun decodesVideoThumbnailWithVideoMimeType() {
    val bitmap = makeBitmap()
    val request = Request.Builder(
      uri = MEDIA_STORE_CONTENT_1_URL,
      resourceId = 0,
      bitmapConfig = ARGB_8888
    )
      .stableKey(MEDIA_STORE_CONTENT_KEY_1)
      .resize(100, 100)
      .build()
    val action = mockAction(request)
    val requestHandler = create("video/")
    requestHandler.load(picasso, action.request, object : Callback {
      override fun onSuccess(result: RequestHandler.Result?) =
        assertBitmapsEqual((result as RequestHandler.Result.Bitmap?)!!.bitmap, bitmap)

      override fun onError(t: Throwable) = fail(t.message)
    })
  }

  @Test fun decodesImageThumbnailWithImageMimeType() {
    val bitmap = makeBitmap(20, 20)
    val request = Request.Builder(
      uri = MEDIA_STORE_CONTENT_1_URL,
      resourceId = 0,
      bitmapConfig = ARGB_8888
    )
      .stableKey(MEDIA_STORE_CONTENT_KEY_1)
      .resize(100, 100)
      .build()
    val action = mockAction(request)
    val requestHandler = create("image/png")
    requestHandler.load(picasso, action.request, object : Callback {
      override fun onSuccess(result: RequestHandler.Result?) =
        assertBitmapsEqual((result as RequestHandler.Result.Bitmap?)!!.bitmap, bitmap)

      override fun onError(t: Throwable) = fail(t.message)
    })
  }

  @Test fun getPicassoKindMicro() {
    assertThat(getPicassoKind(96, 96)).isEqualTo(MICRO)
    assertThat(getPicassoKind(95, 95)).isEqualTo(MICRO)
  }

  @Test fun getPicassoKindMini() {
    assertThat(getPicassoKind(512, 384)).isEqualTo(MINI)
    assertThat(getPicassoKind(100, 100)).isEqualTo(MINI)
  }

  @Test fun getPicassoKindFull() {
    assertThat(getPicassoKind(513, 385)).isEqualTo(FULL)
    assertThat(getPicassoKind(1000, 1000)).isEqualTo(FULL)
    assertThat(getPicassoKind(1000, 384)).isEqualTo(FULL)
    assertThat(getPicassoKind(1000, 96)).isEqualTo(FULL)
    assertThat(getPicassoKind(96, 1000)).isEqualTo(FULL)
  }

  private fun create(mimeType: String): MediaStoreRequestHandler {
    val contentResolver = mock(ContentResolver::class.java)
    `when`(contentResolver.getType(any(Uri::class.java))).thenReturn(mimeType)
    return create(contentResolver)
  }

  private fun create(contentResolver: ContentResolver): MediaStoreRequestHandler {
    `when`(context.contentResolver).thenReturn(contentResolver)
    return MediaStoreRequestHandler(context)
  }

  private fun assertBitmapsEqual(a: Bitmap, b: Bitmap) {
    if (a.height != b.height) fail()
    if (a.width != b.width) fail()

    if (shadowOf(a).description != shadowOf(b).description) fail()
  }
}