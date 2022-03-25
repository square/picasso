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

import android.graphics.Bitmap.createBitmap
import android.graphics.Matrix
import android.os.Build.VERSION
import android.view.Gravity
import androidx.annotation.VisibleForTesting
import androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL
import androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90
import androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE
import androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE
import com.squareup.picasso3.BitmapUtils.shouldResize
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal class MatrixTransformation(private val data: Request) : Transformation {
  override fun transform(source: Bitmap): Bitmap {
    val sourceBitmap = source.bitmap
    val transformedBitmap = transformResult(data, sourceBitmap, source.exifRotation)
    return Bitmap(transformedBitmap, source.loadedFrom, source.exifRotation)
  }

  override fun key() = "matrixTransformation()"

  internal companion object {
    @VisibleForTesting
    @JvmName("-transformResult")
    internal fun transformResult(
      data: Request,
      result: android.graphics.Bitmap,
      exifOrientation: Int
    ): android.graphics.Bitmap {
      val inWidth = result.width
      val inHeight = result.height
      val onlyScaleDown = data.onlyScaleDown

      var drawX = 0
      var drawY = 0
      var drawWidth = inWidth
      var drawHeight = inHeight

      val matrix = Matrix()

      if (data.needsMatrixTransform() || exifOrientation != 0) {
        var targetWidth = data.targetWidth
        var targetHeight = data.targetHeight

        val targetRotation = data.rotationDegrees
        if (targetRotation != 0f) {
          val cosR = cos(Math.toRadians(targetRotation.toDouble()))
          val sinR = sin(Math.toRadians(targetRotation.toDouble()))
          if (data.hasRotationPivot) {
            matrix.setRotate(targetRotation, data.rotationPivotX, data.rotationPivotY)
            // Recalculate dimensions after rotation around pivot point
            val x1T = data.rotationPivotX * (1.0 - cosR) + data.rotationPivotY * sinR
            val y1T = data.rotationPivotY * (1.0 - cosR) - data.rotationPivotX * sinR
            val x2T = x1T + data.targetWidth * cosR
            val y2T = y1T + data.targetWidth * sinR
            val x3T = x1T + data.targetWidth * cosR - data.targetHeight * sinR
            val y3T = y1T + data.targetWidth * sinR + data.targetHeight * cosR
            val x4T = x1T - data.targetHeight * sinR
            val y4T = y1T + data.targetHeight * cosR

            val maxX = max(x4T, max(x3T, max(x1T, x2T)))
            val minX = min(x4T, min(x3T, min(x1T, x2T)))
            val maxY = max(y4T, max(y3T, max(y1T, y2T)))
            val minY = min(y4T, min(y3T, min(y1T, y2T)))
            targetWidth = floor(maxX - minX).toInt()
            targetHeight = floor(maxY - minY).toInt()
          } else {
            matrix.setRotate(targetRotation)
            // Recalculate dimensions after rotation (around origin)
            val x1T = 0.0
            val y1T = 0.0
            val x2T = data.targetWidth * cosR
            val y2T = data.targetWidth * sinR
            val x3T = data.targetWidth * cosR - data.targetHeight * sinR
            val y3T = data.targetWidth * sinR + data.targetHeight * cosR
            val x4T = -(data.targetHeight * sinR)
            val y4T = data.targetHeight * cosR

            val maxX = max(x4T, max(x3T, max(x1T, x2T)))
            val minX = min(x4T, min(x3T, min(x1T, x2T)))
            val maxY = max(y4T, max(y3T, max(y1T, y2T)))
            val minY = min(y4T, min(y3T, min(y1T, y2T)))
            targetWidth = floor(maxX - minX).toInt()
            targetHeight = floor(maxY - minY).toInt()
          }
        }

        // EXIf interpretation should be done before cropping in case the dimensions need to
        // be recalculated; SDK 28+ uses ImageDecoder which handles EXIF orientation
        if (exifOrientation != 0 && VERSION.SDK_INT < 28) {
          val exifRotation = getExifRotation(exifOrientation)
          val exifTranslation = getExifTranslation(exifOrientation)
          if (exifRotation != 0) {
            matrix.preRotate(exifRotation.toFloat())
            if (exifRotation == 90 || exifRotation == 270) {
              // Recalculate dimensions after exif rotation
              val tmpHeight = targetHeight
              targetHeight = targetWidth
              targetWidth = tmpHeight
            }
          }
          if (exifTranslation != 1) {
            matrix.postScale(exifTranslation.toFloat(), 1f)
          }
        }

        if (data.centerCrop) {
          // Keep aspect ratio if one dimension is set to 0
          val widthRatio = if (targetWidth != 0) {
            targetWidth / inWidth.toFloat()
          } else {
            targetHeight / inHeight.toFloat()
          }
          val heightRatio = if (targetHeight != 0) {
            targetHeight / inHeight.toFloat()
          } else {
            targetWidth / inWidth.toFloat()
          }
          val scaleX: Float
          val scaleY: Float
          if (widthRatio > heightRatio) {
            val newSize = ceil((inHeight * (heightRatio / widthRatio)).toDouble()).toInt()
            drawY = if (data.centerCropGravity and Gravity.TOP == Gravity.TOP) {
              0
            } else if (data.centerCropGravity and Gravity.BOTTOM == Gravity.BOTTOM) {
              inHeight - newSize
            } else {
              (inHeight - newSize) / 2
            }
            drawHeight = newSize
            scaleX = widthRatio
            scaleY = targetHeight / drawHeight.toFloat()
          } else if (widthRatio < heightRatio) {
            val newSize = ceil((inWidth * (widthRatio / heightRatio)).toDouble()).toInt()
            drawX = if (data.centerCropGravity and Gravity.LEFT == Gravity.LEFT) {
              0
            } else if (data.centerCropGravity and Gravity.RIGHT == Gravity.RIGHT) {
              inWidth - newSize
            } else {
              (inWidth - newSize) / 2
            }
            drawWidth = newSize
            scaleX = targetWidth / drawWidth.toFloat()
            scaleY = heightRatio
          } else {
            drawX = 0
            drawWidth = inWidth
            scaleY = heightRatio
            scaleX = scaleY
          }
          if (shouldResize(onlyScaleDown, inWidth, inHeight, targetWidth, targetHeight)) {
            matrix.preScale(scaleX, scaleY)
          }
        } else if (data.centerInside) {
          // Keep aspect ratio if one dimension is set to 0
          val widthRatio =
            if (targetWidth != 0) targetWidth / inWidth.toFloat() else targetHeight / inHeight.toFloat()
          val heightRatio =
            if (targetHeight != 0) targetHeight / inHeight.toFloat() else targetWidth / inWidth.toFloat()
          val scale = if (widthRatio < heightRatio) widthRatio else heightRatio
          if (shouldResize(onlyScaleDown, inWidth, inHeight, targetWidth, targetHeight)) {
            matrix.preScale(scale, scale)
          }
        } else if ((targetWidth != 0 || targetHeight != 0) && //
          (targetWidth != inWidth || targetHeight != inHeight)
        ) {
          // If an explicit target size has been specified and they do not match the results bounds,
          // pre-scale the existing matrix appropriately.
          // Keep aspect ratio if one dimension is set to 0.
          val sx =
            if (targetWidth != 0) targetWidth / inWidth.toFloat() else targetHeight / inHeight.toFloat()
          val sy =
            if (targetHeight != 0) targetHeight / inHeight.toFloat() else targetWidth / inWidth.toFloat()
          if (shouldResize(onlyScaleDown, inWidth, inHeight, targetWidth, targetHeight)) {
            matrix.preScale(sx, sy)
          }
        }
      }

      val transformedResult = createBitmap(result, drawX, drawY, drawWidth, drawHeight, matrix, true)
      if (transformedResult != result) {
        result.recycle()
      }
      return transformedResult
    }

    @Suppress("MemberVisibilityCanBePrivate")
    @JvmName("-getExifRotation")
    internal fun getExifRotation(orientation: Int) =
      when (orientation) {
        ORIENTATION_ROTATE_90, ORIENTATION_TRANSPOSE -> 90
        ORIENTATION_ROTATE_180, ORIENTATION_FLIP_VERTICAL -> 180
        ORIENTATION_ROTATE_270, ORIENTATION_TRANSVERSE -> 270
        else -> 0
      }

    @Suppress("MemberVisibilityCanBePrivate")
    @JvmName("-getExifTranslation")
    internal fun getExifTranslation(orientation: Int) =
      when (orientation) {
        ORIENTATION_FLIP_HORIZONTAL, ORIENTATION_FLIP_VERTICAL,
        ORIENTATION_TRANSPOSE, ORIENTATION_TRANSVERSE -> -1
        else -> 1
      }
  }
}
