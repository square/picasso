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
package com.squareup.picasso3.pollexor

import android.net.Uri
import com.squareup.picasso3.Picasso.RequestTransformer
import com.squareup.picasso3.Request
import com.squareup.picasso3.pollexor.PollexorRequestTransformer.Callback
import com.squareup.pollexor.Thumbor
import com.squareup.pollexor.ThumborUrlBuilder
import com.squareup.pollexor.ThumborUrlBuilder.ImageFormat.WEBP

/**
 * A [RequestTransformer] that changes requests to use [Thumbor] for some remote
 * transformations.
 * By default images are only transformed with Thumbor if they have a size set,
 * unless alwaysTransform is set to true
 */
class PollexorRequestTransformer @JvmOverloads constructor(
  private val thumbor: Thumbor,
  private val alwaysTransform: Boolean = false,
  private val callback: Callback = NONE
) : RequestTransformer {
  constructor(thumbor: Thumbor, callback: Callback) : this(thumbor, false, callback)

  override fun transformRequest(request: Request): Request {
    if (request.resourceId != 0) {
      return request // Don't transform resource requests.
    }
    val uri = requireNotNull(request.uri) { "Null uri passed to ${javaClass.canonicalName}" }

    val scheme = uri.scheme
    if ("https" != scheme && "http" != scheme) {
      return request // Thumbor only supports remote images.
    }

    // Only transform requests that have resizes unless `alwaysTransform` is set.
    if (!request.hasSize() && !alwaysTransform) {
      return request
    }

    // Start building a new request for us to mutate.
    val newRequest = request.newBuilder()

    // Create the url builder to use.
    val urlBuilder = thumbor.buildImage(uri.toString())
    callback.configure(urlBuilder)

    // Resize the image to the target size if it has a size.
    if (request.hasSize()) {
      urlBuilder.resize(request.targetWidth, request.targetHeight)
      newRequest.clearResize()
    }

    // If the center inside flag is set, perform that with Thumbor as well.
    if (request.centerInside) {
      urlBuilder.fitIn()
      newRequest.clearCenterInside()
    }

    // Use WebP for downloading.
    urlBuilder.filter(ThumborUrlBuilder.format(WEBP))

    // Update the request with the completed Thumbor URL.
    newRequest.setUri(Uri.parse(urlBuilder.toUrl()))
    return newRequest.build()
  }

  fun interface Callback {
    fun configure(builder: ThumborUrlBuilder)
  }

  companion object {
    private val NONE = Callback { }
  }
}
