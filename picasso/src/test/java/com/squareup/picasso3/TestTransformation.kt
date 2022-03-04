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

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888

internal class TestTransformation(
  private val key: String,
  private val result: Bitmap? = Bitmap.createBitmap(10, 10, ARGB_8888)
) : Transformation {
  override fun transform(source: RequestHandler.Result.Bitmap): RequestHandler.Result.Bitmap {
    val bitmap = source.bitmap
    bitmap.recycle()
    return RequestHandler.Result.Bitmap(result!!, source.loadedFrom, source.exifRotation)
  }

  override fun key(): String = key
}
