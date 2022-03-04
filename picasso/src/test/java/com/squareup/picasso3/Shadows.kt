/*
 * Copyright (C) 2022 Square, Inc.
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
