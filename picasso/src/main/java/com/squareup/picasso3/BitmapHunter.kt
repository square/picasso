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
import com.squareup.picasso3.MemoryPolicy.Companion.shouldReadFromMemoryCache
import com.squareup.picasso3.Picasso.LoadedFrom
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import com.squareup.picasso3.Utils.OWNER_HUNTER
import com.squareup.picasso3.Utils.THREAD_IDLE_NAME
import com.squareup.picasso3.Utils.THREAD_PREFIX
import com.squareup.picasso3.Utils.VERB_DECODED
import com.squareup.picasso3.Utils.VERB_EXECUTING
import com.squareup.picasso3.Utils.VERB_JOINED
import com.squareup.picasso3.Utils.VERB_REMOVED
import com.squareup.picasso3.Utils.VERB_TRANSFORMED
import com.squareup.picasso3.Utils.getLogIdsForHunter
import com.squareup.picasso3.Utils.log
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal open class BitmapHunter(
  val picasso: Picasso,
  private val dispatcher: Dispatcher,
  private val cache: PlatformLruCache,
  action: Action,
  val requestHandler: RequestHandler
) : Runnable {
  val sequence: Int = SEQUENCE_GENERATOR.incrementAndGet()
  var priority: Picasso.Priority = action.request.priority
  var data: Request = action.request
  val key: String = action.request.key
  var retryCount: Int = requestHandler.retryCount

  var action: Action? = action
    private set
  var actions: MutableList<Action>? = null
    private set

  var future: Future<*>? = null
  var result: RequestHandler.Result? = null
    private set
  var exception: Exception? = null
    private set

  val isCancelled: Boolean
    get() = future?.isCancelled ?: false

  override fun run() {
    try {
      updateThreadName(data)

      if (picasso.isLoggingEnabled) {
        log(OWNER_HUNTER, VERB_EXECUTING, getLogIdsForHunter(this))
      }

      result = hunt()
      dispatcher.dispatchComplete(this)
    } catch (e: IOException) {
      exception = e
      if (retryCount > 0) {
        dispatcher.dispatchRetry(this)
      } else {
        dispatcher.dispatchFailed(this)
      }
    } catch (e: Exception) {
      exception = e
      dispatcher.dispatchFailed(this)
    } finally {
      Thread.currentThread().name = THREAD_IDLE_NAME
    }
  }

  fun hunt(): Bitmap? {
    if (shouldReadFromMemoryCache(data.memoryPolicy)) {
      cache[key]?.let { bitmap ->
        picasso.cacheHit()
        if (picasso.isLoggingEnabled) {
          log(OWNER_HUNTER, VERB_DECODED, data.logId(), "from cache")
        }

        return Bitmap(bitmap, LoadedFrom.MEMORY)
      }
    }

    if (retryCount == 0) {
      data = data.newBuilder().networkPolicy(NetworkPolicy.OFFLINE).build()
    }

    val resultReference = AtomicReference<RequestHandler.Result?>()
    val exceptionReference = AtomicReference<Throwable>()

    val latch = CountDownLatch(1)
    try {
      requestHandler.load(
        picasso = picasso,
        request = data,
        callback = object : RequestHandler.Callback {
          override fun onSuccess(result: RequestHandler.Result?) {
            resultReference.set(result)
            latch.countDown()
          }

          override fun onError(t: Throwable) {
            exceptionReference.set(t)
            latch.countDown()
          }
        }
      )

      latch.await()
    } catch (ie: InterruptedException) {
      val interruptedIoException = InterruptedIOException()
      interruptedIoException.initCause(ie)
      throw interruptedIoException
    }

    exceptionReference.get()?.let { throwable ->
      when (throwable) {
        is IOException, is Error, is RuntimeException -> throw throwable
        else -> throw RuntimeException(throwable)
      }
    }

    val result = resultReference.get() as? Bitmap ?: return null
    val bitmap = result.bitmap
    if (picasso.isLoggingEnabled) {
      log(OWNER_HUNTER, VERB_DECODED, data.logId())
    }
    picasso.bitmapDecoded(bitmap)

    val transformations = ArrayList<Transformation>(data.transformations.size + 1)
    if (data.needsMatrixTransform() || result.exifRotation != 0) {
      transformations += MatrixTransformation(data)
    }
    transformations += data.transformations

    val transformedResult =
      applyTransformations(picasso, data, transformations, result) ?: return null
    val transformedBitmap = transformedResult.bitmap
    picasso.bitmapTransformed(transformedBitmap)

    return transformedResult
  }

  fun attach(action: Action) {
    val loggingEnabled = picasso.isLoggingEnabled
    val request = action.request
    if (this.action == null) {
      this.action = action
      if (loggingEnabled) {
        if (actions.isNullOrEmpty()) {
          log(OWNER_HUNTER, VERB_JOINED, request.logId(), "to empty hunter")
        } else {
          log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "))
        }
      }

      return
    }

    if (actions == null) {
      actions = ArrayList(3)
    }
    actions!!.add(action)

    if (loggingEnabled) {
      log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "))
    }

    val actionPriority = action.request.priority
    if (actionPriority.ordinal > priority.ordinal) {
      priority = actionPriority
    }
  }

  fun detach(action: Action) {
    val detached = when {
      this.action === action -> {
        this.action = null
        true
      }
      else -> actions?.remove(action) ?: false
    }

    // The action being detached had the highest priority. Update this
    // hunter's priority with the remaining actions.
    if (detached && action.request.priority == priority) {
      priority = computeNewPriority()
    }

    if (picasso.isLoggingEnabled) {
      log(OWNER_HUNTER, VERB_REMOVED, action.request.logId(), getLogIdsForHunter(this, "from "))
    }
  }

  fun cancel(): Boolean =
    action == null && actions.isNullOrEmpty() && future?.cancel(false) ?: false

  fun shouldRetry(airplaneMode: Boolean, info: NetworkInfo?): Boolean {
    val hasRetries = retryCount > 0
    if (!hasRetries) {
      return false
    }
    retryCount--

    return requestHandler.shouldRetry(airplaneMode, info)
  }

  fun supportsReplay(): Boolean = requestHandler.supportsReplay()

  private fun computeNewPriority(): Picasso.Priority {
    val hasMultiple = actions?.isNotEmpty() ?: false
    val hasAny = action != null || hasMultiple

    // Hunter has no requests, low priority.
    if (!hasAny) {
      return Picasso.Priority.LOW
    }

    var newPriority = action?.request?.priority ?: Picasso.Priority.LOW

    actions?.let { actions ->
      // Index-based loop to avoid allocating an iterator.
      for (i in actions.indices) {
        val priority = actions[i].request.priority
        if (priority.ordinal > newPriority.ordinal) {
          newPriority = priority
        }
      }
    }

    return newPriority
  }

  companion object {
    internal val NAME_BUILDER: ThreadLocal<StringBuilder> = object : ThreadLocal<StringBuilder>() {
      override fun initialValue(): StringBuilder = StringBuilder(THREAD_PREFIX)
    }
    val SEQUENCE_GENERATOR = AtomicInteger()
    internal val ERRORING_HANDLER: RequestHandler = object : RequestHandler() {
      override fun canHandleRequest(data: Request): Boolean = true

      override fun load(picasso: Picasso, request: Request, callback: Callback) {
        callback.onError(IllegalStateException("Unrecognized type of request: $request"))
      }
    }

    fun forRequest(
      picasso: Picasso,
      dispatcher: Dispatcher,
      cache: PlatformLruCache,
      action: Action
    ): BitmapHunter {
      val request = action.request
      val requestHandlers = picasso.requestHandlers

      // Index-based loop to avoid allocating an iterator.
      for (i in requestHandlers.indices) {
        val requestHandler = requestHandlers[i]
        if (requestHandler.canHandleRequest(request)) {
          return BitmapHunter(picasso, dispatcher, cache, action, requestHandler)
        }
      }

      return BitmapHunter(picasso, dispatcher, cache, action, ERRORING_HANDLER)
    }

    fun updateThreadName(data: Request) {
      val name = data.name
      val builder = NAME_BUILDER.get()!!.also {
        it.ensureCapacity(THREAD_PREFIX.length + name.length)
        it.replace(THREAD_PREFIX.length, it.length, name)
      }

      Thread.currentThread().name = builder.toString()
    }

    fun applyTransformations(
      picasso: Picasso,
      data: Request,
      transformations: List<Transformation>,
      result: Bitmap
    ): Bitmap? {
      var res = result

      for (i in transformations.indices) {
        val transformation = transformations[i]
        val newResult = try {
          val transformedResult = transformation.transform(res)
          if (picasso.isLoggingEnabled) {
            log(OWNER_HUNTER, VERB_TRANSFORMED, data.logId(), "from transformations")
          }

          transformedResult
        } catch (e: RuntimeException) {
          Picasso.HANDLER.post {
            throw RuntimeException(
              "Transformation ${transformation.key()} crashed with exception.", e
            )
          }

          return null
        }

        val bitmap = newResult.bitmap
        if (bitmap.isRecycled) {
          Picasso.HANDLER.post {
            throw IllegalStateException(
              "Transformation ${transformation.key()} returned a recycled Bitmap."
            )
          }

          return null
        }

        res = newResult
      }

      return res
    }
  }
}
