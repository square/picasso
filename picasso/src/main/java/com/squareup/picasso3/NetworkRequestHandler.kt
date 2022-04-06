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

import android.net.NetworkInfo
import com.squareup.picasso3.BitmapUtils.decodeStream
import com.squareup.picasso3.NetworkPolicy.Companion.isOfflineOnly
import com.squareup.picasso3.NetworkPolicy.Companion.shouldReadFromDiskCache
import com.squareup.picasso3.NetworkPolicy.Companion.shouldWriteToDiskCache
import com.squareup.picasso3.Picasso.LoadedFrom.DISK
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

internal class NetworkRequestHandler(
  private val callFactory: Call.Factory
) : RequestHandler() {
  override fun canHandleRequest(data: Request): Boolean {
    val uri = data.uri ?: return false
    val scheme = uri.scheme
    return SCHEME_HTTP.equals(scheme, ignoreCase = true) ||
      SCHEME_HTTPS.equals(scheme, ignoreCase = true)
  }

  override fun load(picasso: Picasso, request: Request, callback: Callback) {
    val callRequest = createRequest(request)
    callFactory
      .newCall(callRequest)
      .enqueue(object : okhttp3.Callback {
        override fun onResponse(call: Call, response: Response) {
          if (!response.isSuccessful) {
            callback.onError(ResponseException(response.code, request.networkPolicy))
            return
          }

          // Cache response is only null when the response comes fully from the network. Both
          // completely cached and conditionally cached responses will have a non-null cache
          // response.
          val loadedFrom = if (response.cacheResponse == null) NETWORK else DISK

          // Sometimes response content length is zero when requests are being replayed.
          // Haven't found root cause to this but retrying the request seems safe to do so.
          val body = response.body
          if (loadedFrom == DISK && body!!.contentLength() == 0L) {
            body.close()
            callback.onError(
              ContentLengthException("Received response with 0 content-length header.")
            )
            return
          }
          if (loadedFrom == NETWORK && body!!.contentLength() > 0) {
            picasso.downloadFinished(body.contentLength())
          }
          try {
            val bitmap = decodeStream(body!!.source(), request)
            callback.onSuccess(Result.Bitmap(bitmap, loadedFrom))
          } catch (e: IOException) {
            body!!.close()
            callback.onError(e)
          }
        }

        override fun onFailure(call: Call, e: IOException) {
          callback.onError(e)
        }
      })
  }

  override val retryCount: Int
    get() = 2

  override fun shouldRetry(airplaneMode: Boolean, info: NetworkInfo?): Boolean =
    info == null || info.isConnected

  override fun supportsReplay(): Boolean = true

  private fun createRequest(request: Request): okhttp3.Request {
    var cacheControl: CacheControl? = null
    val networkPolicy = request.networkPolicy
    if (networkPolicy != 0) {
      cacheControl = if (isOfflineOnly(networkPolicy)) {
        CacheControl.FORCE_CACHE
      } else {
        val builder = CacheControl.Builder()
        if (!shouldReadFromDiskCache(networkPolicy)) {
          builder.noCache()
        }
        if (!shouldWriteToDiskCache(networkPolicy)) {
          builder.noStore()
        }
        builder.build()
      }
    }

    val uri = checkNotNull(request.uri) { "request.uri == null" }
    val builder = okhttp3.Request.Builder().url(uri.toString())
    if (cacheControl != null) {
      builder.cacheControl(cacheControl)
    }
    val requestHeaders = request.headers
    if (requestHeaders != null) {
      builder.headers(requestHeaders)
    }
    return builder.build()
  }

  internal class ContentLengthException(message: String) : RuntimeException(message)
  internal class ResponseException(
    val code: Int,
    val networkPolicy: Int
  ) : RuntimeException("HTTP $code")

  private companion object {
    private const val SCHEME_HTTP = "http"
    private const val SCHEME_HTTPS = "https"
  }
}
