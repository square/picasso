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
package com.squareup.picasso3

import android.content.Context
import android.graphics.Bitmap.Config.ARGB_8888
import android.net.NetworkInfo
import android.net.Uri
import android.os.Looper
import android.view.Gravity
import androidx.exifinterface.media.ExifInterface
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.BitmapHunter.Companion.applyTransformations
import com.squareup.picasso3.BitmapHunter.Companion.forRequest
import com.squareup.picasso3.MatrixTransformation.Companion.transformResult
import com.squareup.picasso3.NetworkRequestHandler.ResponseException
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso3.Picasso.Priority.HIGH
import com.squareup.picasso3.Picasso.Priority.LOW
import com.squareup.picasso3.Picasso.Priority.NORMAL
import com.squareup.picasso3.Request.Companion.KEY_SEPARATOR
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import com.squareup.picasso3.TestUtils.ASSET_KEY_1
import com.squareup.picasso3.TestUtils.ASSET_URI_1
import com.squareup.picasso3.TestUtils.BITMAP_RESOURCE_VALUE
import com.squareup.picasso3.TestUtils.CONTACT_KEY_1
import com.squareup.picasso3.TestUtils.CONTACT_PHOTO_KEY_1
import com.squareup.picasso3.TestUtils.CONTACT_PHOTO_URI_1
import com.squareup.picasso3.TestUtils.CONTACT_URI_1
import com.squareup.picasso3.TestUtils.CONTENT_1_URL
import com.squareup.picasso3.TestUtils.CONTENT_KEY_1
import com.squareup.picasso3.TestUtils.CUSTOM_URI
import com.squareup.picasso3.TestUtils.CUSTOM_URI_KEY
import com.squareup.picasso3.TestUtils.EventRecorder
import com.squareup.picasso3.TestUtils.FILE_1_URL
import com.squareup.picasso3.TestUtils.FILE_KEY_1
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_1_URL
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_KEY_1
import com.squareup.picasso3.TestUtils.NOOP_REQUEST_HANDLER
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS
import com.squareup.picasso3.TestUtils.RESOURCE_ID_1
import com.squareup.picasso3.TestUtils.RESOURCE_ID_KEY_1
import com.squareup.picasso3.TestUtils.RESOURCE_ID_URI
import com.squareup.picasso3.TestUtils.RESOURCE_ID_URI_KEY
import com.squareup.picasso3.TestUtils.RESOURCE_TYPE_URI
import com.squareup.picasso3.TestUtils.RESOURCE_TYPE_URI_KEY
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY
import com.squareup.picasso3.TestUtils.URI_1
import com.squareup.picasso3.TestUtils.URI_KEY_1
import com.squareup.picasso3.TestUtils.XML_RESOURCE_VALUE
import com.squareup.picasso3.TestUtils.makeBitmap
import com.squareup.picasso3.TestUtils.makeLoaderWithDrawable
import com.squareup.picasso3.TestUtils.mockAction
import com.squareup.picasso3.TestUtils.mockImageViewTarget
import com.squareup.picasso3.TestUtils.mockPicasso
import com.squareup.picasso3.TestUtils.mockResources
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.IOException
import java.util.concurrent.FutureTask

@RunWith(RobolectricTestRunner::class)
class BitmapHunterTest {
  @Mock internal lateinit var context: Context
  @Mock internal lateinit var dispatcher: Dispatcher
  private lateinit var picasso: Picasso

  private val cache = PlatformLruCache(2048)
  private val bitmap = makeBitmap()

  @Before fun setUp() {
    initMocks(this)
    `when`(context.applicationContext).thenReturn(context)
    picasso = mockPicasso(context, NOOP_REQUEST_HANDLER)
  }

  @Test fun nullDecodeResponseIsError() {
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action, null)
    hunter.run()
    verify(dispatcher).dispatchFailed(hunter)
  }

  @Test fun runWithResultDispatchComplete() {
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action, bitmap)
    hunter.run()
    verify(dispatcher).dispatchComplete(hunter)
  }

  @Test fun runWithNoResultDispatchFailed() {
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action)
    hunter.run()
    verify(dispatcher).dispatchFailed(hunter)
  }

  @Test fun responseExceptionDispatchFailed() {
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = TestableBitmapHunter(
      picasso, dispatcher, cache, action, null, ResponseException(504, 0)
    )
    hunter.run()
    verify(dispatcher).dispatchFailed(hunter)
  }

  @Test fun runWithIoExceptionDispatchRetry() {
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action, null, IOException())
    hunter.run()
    verify(dispatcher).dispatchRetry(hunter)
  }

  @Test fun huntDecodesWhenNotInCache() {
    val eventRecorder = EventRecorder()
    val picasso = picasso.newBuilder().addEventListener(eventRecorder).build()
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action, bitmap)

    val result = hunter.hunt()
    assertThat(cache.missCount()).isEqualTo(1)
    assertThat(result).isNotNull()
    assertThat(result!!.bitmap).isEqualTo(bitmap)
    assertThat(result.loadedFrom).isEqualTo(NETWORK)
    assertThat(eventRecorder.decodedBitmap).isEqualTo(bitmap)
  }

  @Test fun huntReturnsWhenResultInCache() {
    cache[URI_KEY_1 + KEY_SEPARATOR] = bitmap
    val eventRecorder = EventRecorder()
    val picasso = picasso.newBuilder().addEventListener(eventRecorder).build()
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action, bitmap)

    val result = hunter.hunt()
    assertThat(cache.hitCount()).isEqualTo(1)
    assertThat(result).isNotNull()
    assertThat(result!!.bitmap).isEqualTo(bitmap)
    assertThat(result.loadedFrom).isEqualTo(MEMORY)
    assertThat(eventRecorder.decodedBitmap).isNull()
  }

  @Test fun huntUnrecognizedUri() {
    val action = mockAction(picasso, CUSTOM_URI_KEY, CUSTOM_URI)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    try {
      hunter.hunt()
      fail("Unrecognized URI should throw exception.")
    } catch (ignored: IllegalStateException) {
    }
  }

  @Test fun huntDecodesWithRequestHandler() {
    val picasso = mockPicasso(context, CustomRequestHandler())
    val action = mockAction(picasso, CUSTOM_URI_KEY, CUSTOM_URI)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    val result = hunter.hunt()
    assertThat(result!!.bitmap).isEqualTo(bitmap)
  }

  @Test fun attachSingleRequest() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action1)
    assertThat(hunter.action).isEqualTo(action1)
    hunter.detach(action1)
    hunter.attach(action1)
    assertThat(hunter.action).isEqualTo(action1)
    assertThat(hunter.actions).isNull()
  }

  @Test fun attachMultipleRequests() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action1)
    assertThat(hunter.actions).isNull()
    hunter.attach(action2)
    assertThat(hunter.actions).isNotNull()
    assertThat(hunter.actions).hasSize(1)
  }

  @Test fun detachSingleRequest() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action)
    assertThat(hunter.action).isNotNull()
    hunter.detach(action)
    assertThat(hunter.action).isNull()
  }

  @Test fun detachMultipleRequests() {
    val action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action)
    hunter.attach(action2)
    hunter.detach(action2)
    assertThat(hunter.action).isNotNull()
    assertThat(hunter.actions).isNotNull()
    assertThat(hunter.actions).isEmpty()
    hunter.detach(action)
    assertThat(hunter.action).isNull()
  }

  @Test fun cancelSingleRequest() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action1)
    hunter.future = FutureTask(mock(Runnable::class.java), mock(Any::class.java))
    assertThat(hunter.isCancelled).isFalse()
    assertThat(hunter.cancel()).isFalse()
    hunter.detach(action1)
    assertThat(hunter.cancel()).isTrue()
    assertThat(hunter.isCancelled).isTrue()
  }

  @Test fun cancelMultipleRequests() {
    val action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget())
    val hunter = TestableBitmapHunter(picasso, dispatcher, cache, action1)
    hunter.future = FutureTask(mock(Runnable::class.java), mock(Any::class.java))
    hunter.attach(action2)
    assertThat(hunter.isCancelled).isFalse()
    assertThat(hunter.cancel()).isFalse()
    hunter.detach(action1)
    hunter.detach(action2)
    assertThat(hunter.cancel()).isTrue()
    assertThat(hunter.isCancelled).isTrue()
  }

  // ---------------------------------------

  @Test fun forContentProviderRequest() {
    val picasso = mockPicasso(context, ContentStreamRequestHandler(context))
    val action = mockAction(picasso, CONTENT_KEY_1, CONTENT_1_URL)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(ContentStreamRequestHandler::class.java)
  }

  @Test fun forMediaStoreRequest() {
    val picasso = mockPicasso(context, MediaStoreRequestHandler(context))
    val action = mockAction(picasso, MEDIA_STORE_CONTENT_KEY_1, MEDIA_STORE_CONTENT_1_URL)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(MediaStoreRequestHandler::class.java)
  }

  @Test fun forContactsPhotoRequest() {
    val picasso = mockPicasso(context, ContactsPhotoRequestHandler(context))
    val action = mockAction(picasso, CONTACT_KEY_1, CONTACT_URI_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(ContactsPhotoRequestHandler::class.java)
  }

  @Test fun forContactsThumbnailPhotoRequest() {
    val picasso = mockPicasso(context, ContactsPhotoRequestHandler(context))
    val action = mockAction(picasso, CONTACT_PHOTO_KEY_1, CONTACT_PHOTO_URI_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(ContactsPhotoRequestHandler::class.java)
  }

  @Test fun forNetworkRequest() {
    val requestHandler = NetworkRequestHandler(UNUSED_CALL_FACTORY)
    val picasso = mockPicasso(context, requestHandler)
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isSameInstanceAs(requestHandler)
  }

  @Test fun forFileWithAuthorityRequest() {
    val picasso = mockPicasso(context, FileRequestHandler(context))
    val action = mockAction(picasso, FILE_KEY_1, FILE_1_URL)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(FileRequestHandler::class.java)
  }

  @Test fun forAndroidBitmapResourceRequest() {
    val resources = mockResources(BITMAP_RESOURCE_VALUE)
    `when`(context.resources).thenReturn(resources)
    val picasso = mockPicasso(context, ResourceRequestHandler(context))
    val action = mockAction(picasso = picasso, key = RESOURCE_ID_KEY_1, resourceId = RESOURCE_ID_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler::class.java)
  }

  @Test fun forAndroidBitmapResourceUriWithId() {
    val picasso = mockPicasso(context, ResourceRequestHandler(context))
    val action = mockAction(picasso, RESOURCE_ID_URI_KEY, RESOURCE_ID_URI)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler::class.java)
  }

  @Test fun forAndroidBitmapResourceUriWithType() {
    val picasso = mockPicasso(context, ResourceRequestHandler(context))
    val action = mockAction(picasso, RESOURCE_TYPE_URI_KEY, RESOURCE_TYPE_URI)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(ResourceRequestHandler::class.java)
  }

  @Test fun forAndroidXmlResourceRequest() {
    val resources = mockResources(XML_RESOURCE_VALUE)
    `when`(context.resources).thenReturn(resources)
    val requestHandler =
      ResourceDrawableRequestHandler.create(context, makeLoaderWithDrawable(null))
    val picasso = mockPicasso(context, requestHandler)
    val action = mockAction(picasso = picasso, key = RESOURCE_ID_KEY_1, resourceId = RESOURCE_ID_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(ResourceDrawableRequestHandler::class.java)
  }

  @Test fun forAssetRequest() {
    val picasso = mockPicasso(context, AssetRequestHandler(context))
    val action = mockAction(picasso, ASSET_KEY_1, ASSET_URI_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(AssetRequestHandler::class.java)
  }

  @Test fun forFileWithNoPathSegments() {
    val picasso = mockPicasso(context, FileRequestHandler(context))
    val action = mockAction(picasso, "keykeykey", Uri.fromFile(File("/")))
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(FileRequestHandler::class.java)
  }

  @Test fun forCustomRequest() {
    val picasso = mockPicasso(context, CustomRequestHandler())
    val action = mockAction(picasso, CUSTOM_URI_KEY, CUSTOM_URI)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isInstanceOf(CustomRequestHandler::class.java)
  }

  @Test fun forOverrideRequest() {
    val handler = AssetRequestHandler(context)
    // Must use non-mock constructor because that is where Picasso's list of handlers is created.
    val picasso = Picasso(
      context, dispatcher, UNUSED_CALL_FACTORY, null, cache, null, NO_TRANSFORMERS,
      listOf(handler), emptyList(), ARGB_8888, false, false
    )
    val action = mockAction(picasso, ASSET_KEY_1, ASSET_URI_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.requestHandler).isEqualTo(handler)
  }

  @Test fun sequenceIsIncremented() {
    val picasso = mockPicasso(context)
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter1 = forRequest(picasso, dispatcher, cache, action)
    val hunter2 = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter2.sequence).isGreaterThan(hunter1.sequence)
  }

  @Test fun getPriorityWithNoRequests() {
    val requestHandler = NetworkRequestHandler(UNUSED_CALL_FACTORY)
    val picasso = mockPicasso(context, requestHandler)
    val action = mockAction(picasso, URI_KEY_1, URI_1)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    hunter.detach(action)
    assertThat(hunter.action).isNull()
    assertThat(hunter.actions).isNull()
    assertThat(hunter.priority).isEqualTo(LOW)
  }

  @Test fun getPriorityWithSingleRequest() {
    val requestHandler = NetworkRequestHandler(UNUSED_CALL_FACTORY)
    val picasso = mockPicasso(context, requestHandler)
    val action = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, priority = HIGH)
    val hunter = forRequest(picasso, dispatcher, cache, action)
    assertThat(hunter.action).isEqualTo(action)
    assertThat(hunter.actions).isNull()
    assertThat(hunter.priority).isEqualTo(HIGH)
  }

  @Test fun getPriorityWithMultipleRequests() {
    val requestHandler = NetworkRequestHandler(UNUSED_CALL_FACTORY)
    val picasso = mockPicasso(context, requestHandler)
    val action1 = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, priority = NORMAL)
    val action2 = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, priority = HIGH)
    val hunter = forRequest(picasso, dispatcher, cache, action1)
    hunter.attach(action2)
    assertThat(hunter.action).isEqualTo(action1)
    assertThat(hunter.actions).containsExactly(action2)
    assertThat(hunter.priority).isEqualTo(HIGH)
  }

  @Test fun getPriorityAfterDetach() {
    val requestHandler = NetworkRequestHandler(UNUSED_CALL_FACTORY)
    val picasso = mockPicasso(context, requestHandler)
    val action1 = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, priority = NORMAL)
    val action2 = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, priority = HIGH)
    val hunter = forRequest(picasso, dispatcher, cache, action1)
    hunter.attach(action2)
    assertThat(hunter.action).isEqualTo(action1)
    assertThat(hunter.actions).containsExactly(action2)
    assertThat(hunter.priority).isEqualTo(HIGH)
    hunter.detach(action2)
    assertThat(hunter.action).isEqualTo(action1)
    assertThat(hunter.actions).isEmpty()
    assertThat(hunter.priority).isEqualTo(NORMAL)
  }

  @Test fun exifRotation() {
    val data = Request.Builder(URI_1).rotate(-45f).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, ORIENTATION_ROTATE_90)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("rotate 90.0")
  }

  @Test fun exifRotationSizing() {
    val data = Request.Builder(URI_1).resize(5, 10).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, ORIENTATION_ROTATE_90)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).contains("scale 1.0 0.5")
  }

  @Test fun exifRotationNoSizing() {
    val data = Request.Builder(URI_1).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, ORIENTATION_ROTATE_90)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).contains("rotate 90.0")
  }

  @Test fun rotation90Sizing() {
    val data = Request.Builder(URI_1).rotate(90f).resize(5, 10).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, 0)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).contains("scale 1.0 0.5")
  }

  @Test fun rotation180Sizing() {
    val data = Request.Builder(URI_1).rotate(180f).resize(5, 10).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, 0)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).contains("scale 0.5 1.0")
  }

  @Test fun rotation90WithPivotSizing() {
    val data = Request.Builder(URI_1).rotate(90f, 0f, 10f).resize(5, 10).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, 0)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).contains("scale 1.0 0.5")
  }

  @Test fun exifVerticalFlip() {
    val data = Request.Builder(URI_1).rotate(-45f).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, ExifInterface.ORIENTATION_FLIP_VERTICAL)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.postOperations).containsExactly("scale -1.0 1.0")
    assertThat(shadowMatrix.preOperations).containsExactly("rotate 180.0")
  }

  @Test fun exifHorizontalFlip() {
    val data = Request.Builder(URI_1).rotate(-45f).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.postOperations).containsExactly("scale -1.0 1.0")
    assertThat(shadowMatrix.preOperations).doesNotContain("rotate 180.0")
    assertThat(shadowMatrix.preOperations).doesNotContain("rotate 90.0")
    assertThat(shadowMatrix.preOperations).doesNotContain("rotate 270.0")
  }

  @Test fun exifTranspose() {
    val data = Request.Builder(URI_1).rotate(-45f).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, ExifInterface.ORIENTATION_TRANSPOSE)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.postOperations).containsExactly("scale -1.0 1.0")
    assertThat(shadowMatrix.preOperations).containsExactly("rotate 90.0")
  }

  @Test fun exifTransverse() {
    val data = Request.Builder(URI_1).rotate(-45f).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, ExifInterface.ORIENTATION_TRANSVERSE)
    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.postOperations).containsExactly("scale -1.0 1.0")
    assertThat(shadowMatrix.preOperations).containsExactly("rotate 270.0")
  }

  @Test fun keepsAspectRationWhileResizingWhenDesiredWidthIs0() {
    val request = Request.Builder(URI_1).resize(20, 0).build()
    val source = android.graphics.Bitmap.createBitmap(40, 20, ARGB_8888)

    val result = transformResult(request, source, 0)

    val shadowBitmap = shadowOf(result)
    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.5 0.5")
  }

  @Test fun keepsAspectRationWhileResizingWhenDesiredHeightIs0() {
    val request = Request.Builder(URI_1).resize(0, 10).build()
    val source = android.graphics.Bitmap.createBitmap(40, 20, ARGB_8888)

    val result = transformResult(request, source, 0)

    val shadowBitmap = shadowOf(result)
    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.5 0.5")
  }

  @Test fun centerCropResultMatchesTargetSize() {
    val request = Request.Builder(URI_1).resize(1080, 642).centerCrop().build()
    val source = android.graphics.Bitmap.createBitmap(640, 640, ARGB_8888)

    val result = transformResult(request, source, 0)

    assertThat(result.width).isEqualTo(1080)
    assertThat(result.height).isEqualTo(642)
  }

  @Test fun centerCropResultMatchesTargetSizeWhileDesiredWidthIs0() {
    val request = Request.Builder(URI_1).resize(0, 642).centerCrop().build()
    val source = android.graphics.Bitmap.createBitmap(640, 640, ARGB_8888)

    val result = transformResult(request, source, 0)

    assertThat(result.width).isEqualTo(642)
    assertThat(result.height).isEqualTo(642)
  }

  @Test fun centerCropResultMatchesTargetSizeWhileDesiredHeightIs0() {
    val request = Request.Builder(URI_1).resize(1080, 0).centerCrop().build()
    val source = android.graphics.Bitmap.createBitmap(640, 640, ARGB_8888)

    val result = transformResult(request, source, 0)

    assertThat(result.width).isEqualTo(1080)
    assertThat(result.height).isEqualTo(1080)
  }

  @Test fun centerInsideResultMatchesTargetSizeWhileDesiredWidthIs0() {
    val request = Request.Builder(URI_1).resize(0, 642).centerInside().build()
    val source = android.graphics.Bitmap.createBitmap(640, 640, ARGB_8888)

    val result = transformResult(request, source, 0)

    assertThat(result.width).isEqualTo(642)
    assertThat(result.height).isEqualTo(642)
  }

  @Test fun centerInsideResultMatchesTargetSizeWhileDesiredHeightIs0() {
    val request = Request.Builder(URI_1).resize(1080, 0).centerInside().build()
    val source = android.graphics.Bitmap.createBitmap(640, 640, ARGB_8888)

    val result = transformResult(request, source, 0)

    assertThat(result.width).isEqualTo(1080)
    assertThat(result.height).isEqualTo(1080)
  }

  @Test fun exifRotationWithManualRotation() {
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val data = Request.Builder(URI_1).rotate(-45f).build()

    val result = transformResult(data, source, ORIENTATION_ROTATE_90)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("rotate 90.0")
    assertThat(shadowMatrix.setOperations).containsEntry("rotate", "-45.0")
  }

  @Test fun rotation() {
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val data = Request.Builder(URI_1).rotate(-45f).build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.setOperations).containsEntry("rotate", "-45.0")
  }

  @Test fun pivotRotation() {
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val data = Request.Builder(URI_1).rotate(-45f, 10f, 10f).build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.setOperations).containsEntry("rotate", "-45.0 10.0 10.0")
  }

  @Test fun resize() {
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val data = Request.Builder(URI_1).resize(20, 15).build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 2.0 1.5")
  }

  @Test fun centerCropTallTooSmall() {
    val source = android.graphics.Bitmap.createBitmap(10, 20, ARGB_8888)
    val data = Request.Builder(URI_1).resize(40, 40).centerCrop().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(0)
    assertThat(shadowBitmap.createdFromY).isEqualTo(5)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(10)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(10)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 4.0 4.0")
  }

  @Test fun centerCropTallTooLarge() {
    val source = android.graphics.Bitmap.createBitmap(100, 200, ARGB_8888)
    val data = Request.Builder(URI_1).resize(50, 50).centerCrop().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(0)
    assertThat(shadowBitmap.createdFromY).isEqualTo(50)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(100)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(100)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.5 0.5")
  }

  @Test fun centerCropWideTooSmall() {
    val source = android.graphics.Bitmap.createBitmap(20, 10, ARGB_8888)
    val data = Request.Builder(URI_1).resize(40, 40).centerCrop().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(5)
    assertThat(shadowBitmap.createdFromY).isEqualTo(0)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(10)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(10)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 4.0 4.0")
  }

  @Test fun centerCropWithGravityHorizontalLeft() {
    val source = android.graphics.Bitmap.createBitmap(20, 10, ARGB_8888)
    val data = Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.LEFT).build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(0)
    assertThat(shadowBitmap.createdFromY).isEqualTo(0)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(10)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(10)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 4.0 4.0")
  }

  @Test fun centerCropWithGravityHorizontalRight() {
    val source = android.graphics.Bitmap.createBitmap(20, 10, ARGB_8888)
    val data = Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.RIGHT).build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(10)
    assertThat(shadowBitmap.createdFromY).isEqualTo(0)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(10)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(10)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 4.0 4.0")
  }

  @Test fun centerCropWithGravityVerticalTop() {
    val source = android.graphics.Bitmap.createBitmap(10, 20, ARGB_8888)
    val data = Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.TOP).build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(0)
    assertThat(shadowBitmap.createdFromY).isEqualTo(0)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(10)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(10)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 4.0 4.0")
  }

  @Test fun centerCropWithGravityVerticalBottom() {
    val source = android.graphics.Bitmap.createBitmap(10, 20, ARGB_8888)
    val data = Request.Builder(URI_1).resize(40, 40).centerCrop(Gravity.BOTTOM).build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(0)
    assertThat(shadowBitmap.createdFromY).isEqualTo(10)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(10)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(10)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 4.0 4.0")
  }

  @Test fun centerCropWideTooLarge() {
    val source = android.graphics.Bitmap.createBitmap(200, 100, ARGB_8888)
    val data = Request.Builder(URI_1).resize(50, 50).centerCrop().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)
    assertThat(shadowBitmap.createdFromX).isEqualTo(50)
    assertThat(shadowBitmap.createdFromY).isEqualTo(0)
    assertThat(shadowBitmap.createdFromWidth).isEqualTo(100)
    assertThat(shadowBitmap.createdFromHeight).isEqualTo(100)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.5 0.5")
  }

  @Test fun centerInsideTallTooSmall() {
    val source = android.graphics.Bitmap.createBitmap(20, 10, ARGB_8888)
    val data = Request.Builder(URI_1).resize(50, 50).centerInside().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 2.5 2.5")
  }

  @Test fun centerInsideTallTooLarge() {
    val source = android.graphics.Bitmap.createBitmap(100, 50, ARGB_8888)
    val data = Request.Builder(URI_1).resize(50, 50).centerInside().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.5 0.5")
  }

  @Test fun centerInsideWideTooSmall() {
    val source = android.graphics.Bitmap.createBitmap(10, 20, ARGB_8888)
    val data = Request.Builder(URI_1).resize(50, 50).centerInside().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)
    assertThat(shadowMatrix.preOperations).containsExactly("scale 2.5 2.5")
  }

  @Test fun centerInsideWideTooLarge() {
    val source = android.graphics.Bitmap.createBitmap(50, 100, ARGB_8888)
    val data = Request.Builder(URI_1).resize(50, 50).centerInside().build()

    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)

    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.5 0.5")
  }

  @Test fun onlyScaleDownOriginalBigger() {
    val source = android.graphics.Bitmap.createBitmap(100, 100, ARGB_8888)
    val data = Request.Builder(URI_1).resize(50, 50).onlyScaleDown().build()
    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)

    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.5 0.5")
  }

  @Test fun onlyScaleDownOriginalSmaller() {
    val source = android.graphics.Bitmap.createBitmap(50, 50, ARGB_8888)
    val data = Request.Builder(URI_1).resize(100, 100).onlyScaleDown().build()
    val result = transformResult(data, source, 0)
    assertThat(result).isSameInstanceAs(source)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isNull()
    assertThat(shadowBitmap.createdFromBitmap).isNotSameInstanceAs(source)
  }

  @Test fun onlyScaleDownOriginalSmallerWidthIs0() {
    val source = android.graphics.Bitmap.createBitmap(50, 50, ARGB_8888)
    val data = Request.Builder(URI_1).resize(0, 60).onlyScaleDown().build()
    val result = transformResult(data, source, 0)
    assertThat(result).isSameInstanceAs(source)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isNull()
  }

  @Test fun onlyScaleDownOriginalSmallerHeightIs0() {
    val source = android.graphics.Bitmap.createBitmap(50, 50, ARGB_8888)
    val data = Request.Builder(URI_1).resize(60, 0).onlyScaleDown().build()
    val result = transformResult(data, source, 0)
    assertThat(result).isSameInstanceAs(source)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isNull()
  }

  @Test fun onlyScaleDownOriginalBiggerWidthIs0() {
    val source = android.graphics.Bitmap.createBitmap(50, 50, ARGB_8888)
    val data = Request.Builder(URI_1).resize(0, 40).onlyScaleDown().build()
    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)

    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.8 0.8")
  }

  @Test fun onlyScaleDownOriginalBiggerHeightIs0() {
    val source = android.graphics.Bitmap.createBitmap(50, 50, ARGB_8888)
    val data = Request.Builder(URI_1).resize(40, 0).onlyScaleDown().build()
    val result = transformResult(data, source, 0)

    val shadowBitmap = shadowOf(result)
    assertThat(shadowBitmap.createdFromBitmap).isSameInstanceAs(source)

    val matrix = shadowBitmap.createdFromMatrix
    val shadowMatrix = shadowOf(matrix)

    assertThat(shadowMatrix.preOperations).containsExactly("scale 0.8 0.8")
  }

  @Test fun reusedBitmapIsNotRecycled() {
    val data = Request.Builder(URI_1).build()
    val source = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = transformResult(data, source, 0)
    assertThat(result).isSameInstanceAs(source)
    assertThat(result.isRecycled).isFalse()
  }

  @Test fun crashingOnTransformationThrows() {
    val badTransformation = object : Transformation {
      override fun transform(source: Bitmap): Bitmap {
        throw NullPointerException("hello")
      }

      override fun key(): String {
        return "test"
      }
    }
    val transformations = listOf(badTransformation)
    val original = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = RequestHandler.Result.Bitmap(original, MEMORY, 0)
    val data = Request.Builder(URI_1).build()
    try {
      applyTransformations(picasso, data, transformations, result)
      shadowOf(Looper.getMainLooper()).idle()
      fail("Expected exception to be thrown.")
    } catch (e: RuntimeException) {
      assertThat(e)
        .hasMessageThat()
        .isEqualTo("Transformation ${badTransformation.key()} crashed with exception.")
    }
  }

  @Test fun recycledTransformationBitmapThrows() {
    val badTransformation: Transformation = object : Transformation {
      override fun transform(source: Bitmap): Bitmap {
        source.bitmap.recycle()
        return source
      }

      override fun key(): String {
        return "test"
      }
    }
    val transformations = listOf(badTransformation)
    val original = android.graphics.Bitmap.createBitmap(10, 10, ARGB_8888)
    val result = RequestHandler.Result.Bitmap(original, MEMORY, 0)
    val data = Request.Builder(URI_1).build()
    try {
      applyTransformations(picasso, data, transformations, result)
      shadowOf(Looper.getMainLooper()).idle()
      fail("Expected exception to be thrown.")
    } catch (e: RuntimeException) {
      assertThat(e)
        .hasMessageThat()
        .isEqualTo("Transformation ${badTransformation.key()} returned a recycled Bitmap.")
    }
  }

  // TODO: fix regression from https://github.com/square/picasso/pull/2137
  // @Test public void transformDrawables() {
  //  final AtomicInteger transformationCount = new AtomicInteger();
  //  Transformation identity = new Transformation() {
  //    @Override public RequestHandler.Result.Bitmap transform(RequestHandler.Result.Bitmap source) {
  //      transformationCount.incrementAndGet();
  //      return source;
  //    }
  //
  //    @Override public String key() {
  //      return "test";
  //    }
  //  };
  //  List<Transformation> transformations = asList(identity, identity, identity);
  //  Drawable original = new BitmapDrawable(Bitmap.createBitmap(10, 10, ARGB_8888));
  //  RequestHandler.Result.Bitmap result = new RequestHandler.Result.Bitmap(original, MEMORY);
  //  Request data = new Request.Builder(URI_1).build();
  //  BitmapHunter.applyTransformations(picasso, data, transformations, result);
  //  assertThat(transformationCount.get()).isEqualTo(3);
  // }

  internal class TestableBitmapHunter(
    picasso: Picasso,
    dispatcher: Dispatcher,
    cache: PlatformLruCache,
    action: Action,
    result: android.graphics.Bitmap? = null,
    exception: Exception? = null,
    shouldRetry: Boolean = false,
    supportsReplay: Boolean = false
  ) : BitmapHunter(
    picasso, dispatcher, cache, action,
    TestableRequestHandler(result, exception, shouldRetry, supportsReplay)
  )

  private class TestableRequestHandler internal constructor(
    private val bitmap: android.graphics.Bitmap?,
    private val exception: Exception?,
    private val shouldRetry: Boolean,
    private val supportsReplay: Boolean
  ) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
      return true
    }

    override fun load(picasso: Picasso, request: Request, callback: Callback) {
      if (exception != null) {
        callback.onError(exception)
      } else {
        callback.onSuccess(Bitmap(bitmap!!, NETWORK))
      }
    }

    override val retryCount: Int
      get() = 1

    override fun shouldRetry(airplaneMode: Boolean, info: NetworkInfo?): Boolean {
      return shouldRetry
    }

    override fun supportsReplay(): Boolean {
      return supportsReplay
    }
  }

  private inner class CustomRequestHandler : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
      return CUSTOM_URI.scheme == data.uri!!.scheme
    }

    override fun load(picasso: Picasso, request: Request, callback: Callback) {
      callback.onSuccess(Result.Bitmap(bitmap, MEMORY))
    }
  }
}
