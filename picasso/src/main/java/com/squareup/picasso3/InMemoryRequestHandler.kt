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

import android.graphics.Bitmap
import com.squareup.picasso3.Picasso.LoadedFrom
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import java.io.IOException

/**
 * A [RequestHandler] which loads images from assets but attempts to emulate the
 * subtleties of a real HTTP client and the disk cache.
 */
class InMemoryRequestHandler : RequestHandler() {

  private val requests: MutableMap<String, Pair<Bitmap, LoadedFrom>> = mutableMapOf()

  override fun canHandleRequest(data: Request) = requests.containsKey(data.uri.toString())

  @Throws(IOException::class)
  override fun load(picasso: Picasso, request: Request, callback: Callback) {
    val (bitmap, loadedFrom) = requests[request.uri.toString()]!!
    return callback.onSuccess(Result.Bitmap(bitmap.copy(bitmap.config, true), loadedFrom))
  }

  fun addRequest(
    url: String,
    bitmap: Bitmap,
    loadedFrom: LoadedFrom = MEMORY
  ) {
    requests[url] = bitmap to loadedFrom
  }
}
