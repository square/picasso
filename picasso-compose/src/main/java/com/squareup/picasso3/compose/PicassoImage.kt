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

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.RequestCreator
import java.io.File

@Composable
fun PicassoImage(
  picasso: Picasso,
  uri: Uri,
  requestModifier: ((RequestCreator) -> RequestCreator)? = null,
  contentDescription: String? = null,
  modifier: Modifier = Modifier,
  targetImageSize: Size = Size.Unspecified,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null
) {
  PicassoImage(
    picasso = StablePicasso(picasso),
    key = uri,
    request = { it.load(uri).let { requestModifier?.invoke(it) ?: it } },
    contentDescription = contentDescription,
    modifier = modifier,
    targetImageSize = targetImageSize,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId
  )
}

@Composable
fun PicassoImage(
  picasso: Picasso,
  url: String,
  requestModifier: ((RequestCreator) -> RequestCreator)? = null,
  contentDescription: String? = null,
  modifier: Modifier = Modifier,
  targetImageSize: Size = Size.Unspecified,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null
) {
  PicassoImage(
    picasso = StablePicasso(picasso),
    key = url,
    request = { it.load(url).let { requestModifier?.invoke(it) ?: it } },
    contentDescription = contentDescription,
    modifier = modifier,
    targetImageSize = targetImageSize,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId
  )
}

@Composable
fun PicassoImage(
  picasso: Picasso,
  file: File,
  requestModifier: ((RequestCreator) -> RequestCreator)? = null,
  contentDescription: String? = null,
  modifier: Modifier = Modifier,
  targetImageSize: Size = Size.Unspecified,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null
) {
  PicassoImage(
    picasso = StablePicasso(picasso),
    key = file,
    request = { it.load(file).let { requestModifier?.invoke(it) ?: it } },
    contentDescription = contentDescription,
    modifier = modifier,
    targetImageSize = targetImageSize,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId
  )
}

@Composable
fun PicassoImage(
  picasso: Picasso,
  @DrawableRes resourceId: Int,
  requestModifier: ((RequestCreator) -> RequestCreator)? = null,
  contentDescription: String? = null,
  modifier: Modifier = Modifier,
  targetImageSize: Size = Size.Unspecified,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null
) {
  PicassoImage(
    picasso = StablePicasso(picasso),
    key = resourceId,
    request = { it.load(resourceId).let { requestModifier?.invoke(it) ?: it } },
    contentDescription = contentDescription,
    modifier = modifier,
    targetImageSize = targetImageSize,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId
  )
}

@Composable
private fun PicassoImage(
  picasso: StablePicasso,
  key: Any?,
  request: ((Picasso) -> RequestCreator),
  contentDescription: String?,
  modifier: Modifier = Modifier,
  targetImageSize: Size = Size.Unspecified,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null
) {
  val picassoPainter = picasso.value.rememberPainter(
    request = {
      request(it).apply {
        errorId?.let(::error)
        placeholderId?.let(::placeholder)

        if (targetImageSize.isSpecified) {
          resize(targetImageSize.width.toInt(), targetImageSize.height.toInt())
        }
      }
    },
    key = key,
    onError = onError,
    optimizeCanvasSize = targetImageSize.isUnspecified
  )

  Image(
    modifier = modifier,
    painter = picassoPainter,
    contentDescription = contentDescription,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter
  )
}

@Stable
private class StablePicasso(val value: Picasso) {
  operator fun component1(): Picasso = value
}
