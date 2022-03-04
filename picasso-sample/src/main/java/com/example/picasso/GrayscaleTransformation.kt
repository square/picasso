/*
 * Copyright (C) 2014 Square, Inc.
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
package com.example.picasso

import android.graphics.Bitmap.createBitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.PorterDuff.Mode.MULTIPLY
import android.graphics.PorterDuffXfermode
import android.graphics.Shader.TileMode.REPEAT
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.RequestHandler.Result
import com.squareup.picasso3.Transformation
import java.io.IOException

class GrayscaleTransformation(private val picasso: Picasso) : Transformation {
  override fun transform(source: Result.Bitmap): Result.Bitmap {
    val bitmap = source.bitmap

    val result = createBitmap(bitmap.width, bitmap.height, bitmap.config)
    val noise = try {
      picasso.load(R.drawable.noise).get()!!
    } catch (e: IOException) {
      throw RuntimeException("Failed to apply transformation! Missing resource.")
    }

    val colorMatrix = ColorMatrix().apply { setSaturation(0f) }

    val paint = Paint(ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }

    val canvas = Canvas(result)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)

    paint.apply {
      colorFilter = null
      shader = BitmapShader(noise, REPEAT, REPEAT)
      xfermode = PorterDuffXfermode(MULTIPLY)
    }

    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)

    bitmap.recycle()
    noise.recycle()

    return Result.Bitmap(result, source.loadedFrom, source.exifRotation)
  }

  override fun key() = "grayscaleTransformation()"
}
