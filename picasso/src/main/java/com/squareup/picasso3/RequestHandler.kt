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
package com.squareup.picasso3

import android.net.NetworkInfo
import com.squareup.picasso3.Picasso.LoadedFrom
import java.io.IOException

/**
 * `RequestHandler` allows you to extend Picasso to load images in ways that are not
 * supported by default in the library.
 *
 * <h2>Usage</h2>
 * `RequestHandler` must be subclassed to be used. You will have to override two methods
 * ([canHandleRequest] and [load]) with your custom logic to load images.
 *
 * You should then register your [RequestHandler] using
 * [Picasso.Builder.addRequestHandler]
 *
 * **Note:** This is a beta feature. The API is subject to change in a backwards incompatible
 * way at any time.
 *
 * @see Picasso.Builder.addRequestHandler
 */
abstract class RequestHandler {
  /**
   * [Result] represents the result of a [load] call in a [RequestHandler].
   *
   * @see RequestHandler
   * @see [load]
   */
  sealed class Result constructor(
    /**
     * Returns the resulting [Picasso.LoadedFrom] generated from a [load] call.
     */
    val loadedFrom: LoadedFrom,
    /**
     * Returns the resulting EXIF rotation generated from a [load] call.
     */
    val exifRotation: Int = 0
  ) {
    class Bitmap constructor(
      val bitmap: android.graphics.Bitmap,
      loadedFrom: LoadedFrom,
      exifRotation: Int = 0
    ) : Result(loadedFrom, exifRotation)

    class Drawable constructor(
      val drawable: android.graphics.drawable.Drawable,
      loadedFrom: LoadedFrom,
      exifRotation: Int = 0
    ) : Result(loadedFrom, exifRotation)
  }

  interface Callback {
    fun onSuccess(result: Result?)
    fun onError(t: Throwable)
  }

  /**
   * Whether or not this [RequestHandler] can handle a request with the given [Request].
   */
  abstract fun canHandleRequest(data: Request): Boolean

  /**
   * Loads an image for the given [Request].
   * @param request the data from which the image should be resolved.
   */
  @Throws(IOException::class)
  abstract fun load(
    picasso: Picasso,
    request: Request,
    callback: Callback
  )

  open val retryCount = 0

  open fun shouldRetry(
    airplaneMode: Boolean,
    info: NetworkInfo?
  ) = false

  open fun supportsReplay() = false
}
