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
package com.squareup.picasso3.compose

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import com.squareup.picasso3.RequestCreator
import kotlin.math.roundToInt

internal fun RequestCreator.withProperties(size: Size, contentScale: ContentScale, alignment: Alignment) =
  apply {
    val targetWidth = size.width.roundToInt()
    val targetHeight = size.height.roundToInt()

    when (contentScale) {
      ContentScale.Fit -> {
        resize(targetWidth, targetHeight)
        centerInside()
      }
      ContentScale.Inside -> {
        resize(targetWidth, targetHeight)
        centerInside()
        onlyScaleDown()
      }
      ContentScale.Crop -> {
        resize(targetWidth, targetHeight)
        centerCrop(alignment.toGravity())
      }
      ContentScale.FillWidth -> {
        resize(targetWidth, 0)
      }
      ContentScale.FillHeight -> {
        resize(0, targetHeight)
      }
      ContentScale.FillBounds -> {
        resize(targetWidth, targetHeight)
      }
    }
  }
