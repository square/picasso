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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.RequestCreator
import kotlinx.coroutines.launch
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
  crossfadeSpec: FiniteAnimationSpec<Float> = tween(200),
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null,
  placeholder: @Composable (() -> Unit)? = null,
  error: @Composable ((Exception) -> Unit)? = null
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
    crossfadeSpec = crossfadeSpec,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId,
    placeholder = placeholder,
    error = error
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
  crossfadeSpec: FiniteAnimationSpec<Float> = tween(200),
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null,
  placeholder: @Composable (() -> Unit)? = null,
  error: @Composable ((Exception) -> Unit)? = null
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
    crossfadeSpec = crossfadeSpec,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId,
    placeholder = placeholder,
    error = error
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
  crossfadeSpec: FiniteAnimationSpec<Float> = tween(200),
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null,
  placeholder: @Composable (() -> Unit)? = null,
  error: @Composable ((Exception) -> Unit)? = null
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
    crossfadeSpec = crossfadeSpec,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId,
    placeholder = placeholder,
    error = error
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
  crossfadeSpec: FiniteAnimationSpec<Float> = tween(200),
  onError: ((Exception) -> Unit)? = null,
  @DrawableRes placeholderId: Int? = null,
  @DrawableRes errorId: Int? = null,
  placeholder: @Composable (() -> Unit)? = null,
  error: @Composable ((Exception) -> Unit)? = null
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
    crossfadeSpec = crossfadeSpec,
    onError = onError,
    placeholderId = placeholderId,
    errorId = errorId,
    placeholder = placeholder,
    error = error
  )
}

@Composable private fun PicassoImage(
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
  @DrawableRes errorId: Int? = null,
  crossfadeSpec: FiniteAnimationSpec<Float> = tween(200),
  placeholder: @Composable (() -> Unit)? = null,
  error: @Composable ((Exception) -> Unit)? = null
) {
  var exception by remember<MutableState<Exception?>>(key) { mutableStateOf(null) }
  val picassoPainter = picasso.value.rememberPainter(
    request = {
      request(it).apply {
        if (targetImageSize.isSpecified) {
          resize(targetImageSize.width.toInt(), targetImageSize.height.toInt())
          onlyScaleDown()
        }

        noFade()
      }
    },
    onError = { onError?.invoke(it); exception = it },
    optimizeCanvasSize = targetImageSize.isUnspecified
  )

  Box {
    val scope = rememberCoroutineScope()
    var removePlaceholder by remember(key) { mutableStateOf(false) }
    var firstFrame by remember(key) { mutableStateOf(true) }
    val placeholderFade by remember(key) {
      derivedStateOf {
        if (!removePlaceholder && picassoPainter.intrinsicSize.isSpecified) {
          Animatable(1F).also {
            scope.launch {
              it.animateTo(0F, animationSpec = crossfadeSpec)
              if (placeholder != null) {
                removePlaceholder = true
              }
            }
          }
        } else null
      }
    }

    Image(
      modifier = modifier
        .graphicsLayer { placeholderFade?.let { this.alpha = 1F - it.value } }
        .drawWithContent {
          drawContent()
          if (firstFrame) {
            if (picassoPainter.intrinsicSize.isSpecified) {
              removePlaceholder = true
            }
            firstFrame = false
          }
        },
      painter = picassoPainter,
      contentDescription = contentDescription,
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter
    )

    @Composable
    fun ResourceImage(id: Int) = Image(
      modifier = modifier,
      painter = painterResource(id = id),
      contentDescription = contentDescription,
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter
    )

    val placeholderComposable = placeholder ?: placeholderId?.let { id -> { ResourceImage(id = id) } }
    placeholderComposable?.let { placeholder ->
      if (!removePlaceholder) {
        Box(
          modifier = modifier
            .graphicsLayer { this.alpha = placeholderFade?.value ?: 1F }
            .drawWithContent {
              if (!removePlaceholder) {
                drawContent()
              }
            }
        ) {
          placeholder.invoke()
        }
      }
    }

    val errorComposable = error ?: errorId?.let { id -> { ResourceImage(id = id) } }
    errorComposable?.let { error ->
      exception?.let {
        val animateIn = remember(key) { Animatable(0F) }
        LaunchedEffect(key) {
          animateIn.animateTo(1F, animationSpec = crossfadeSpec)
        }

        Box(
          modifier = modifier.graphicsLayer { this.alpha = animateIn.value }
        ) {
          error.invoke(it)
        }
      }
    }
  }
}

@Stable
private class StablePicasso(val value: Picasso) {
  operator fun component1(): Picasso = value
}
