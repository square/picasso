package com.squareup.picasso3

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.squareup.picasso3.TestUtils.makeBitmap
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

object Shadows {
  @Implements(MediaStore.Video.Thumbnails::class)
  object ShadowVideoThumbnails {
    @Implementation
    @JvmStatic
    fun getThumbnail(
      cr: ContentResolver,
      origId: Long,
      kind: Int,
      options: BitmapFactory.Options
    ): Bitmap = makeBitmap()
  }

  @Implements(MediaStore.Images.Thumbnails::class)
  object ShadowImageThumbnails {
    @Implementation
    @JvmStatic
    fun getThumbnail(
      cr: ContentResolver,
      origId: Long,
      kind: Int,
      options: BitmapFactory.Options
    ): Bitmap = makeBitmap(20, 20)
  }
}