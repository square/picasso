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
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class BitmapHunter(
  val picasso: Picasso,
  val key: String,
  var data: Request,
  private val dispatcher: Dispatcher,
  private val cache: PlatformLruCache,
  private val requestHandler: RequestHandler,
  private var retryCount: Int,
  action: Action?,
  priority: Picasso.Priority
) : Runnable {
  companion object {
    val NAME_BUILDER: ThreadLocal<StringBuilder> = object : ThreadLocal<StringBuilder>() {
      override fun initialValue(): StringBuilder = StringBuilder(Utils.THREAD_PREFIX)
    }
    val SEQUENCE_GENERATOR = AtomicInteger()
    val ERRORING_HANDLER: RequestHandler = object : RequestHandler() {
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
      val requestHandlers = picasso.getRequestHandlers()

      // Index-based loop to avoid allocating an iterator.
      for (i in 0..requestHandlers.lastIndex) {
        val requestHandler = requestHandlers[i]
        if (requestHandler.canHandleRequest(request)) {
          return BitmapHunter(
            picasso = picasso,
            key = action.request.key,
            data = action.request,
            dispatcher = dispatcher,
            cache = cache,
            requestHandler = requestHandler,
            retryCount = requestHandler.retryCount,
            action = action,
            priority = action.request.priority
          )
        }
      }

      return BitmapHunter(
        picasso = picasso,
        key = action.request.key,
        data = action.request,
        dispatcher = dispatcher,
        cache = cache,
        requestHandler = ERRORING_HANDLER,
        retryCount = ERRORING_HANDLER.retryCount,
        action = action,
        priority = action.request.priority
      )
    }

    fun updateThreadName(data: Request) {
      val name = data.name
      val builder = NAME_BUILDER.get()?.also {
        it.ensureCapacity(Utils.THREAD_PREFIX.length + name.length)
        it.replace(Utils.THREAD_PREFIX.length, it.length, name)
      } ?: return

      Thread.currentThread().name = builder.toString()
    }

    fun applyTransformations(
      picasso: Picasso,
      data: Request,
      transformations: List<Transformation>,
      result: RequestHandler.Result.Bitmap
    ): RequestHandler.Result.Bitmap? {
      var res = result

      for (i in 0..transformations.lastIndex) {
        val transformation = transformations[i]
        val newResult = try {
          val transformedResult = transformation.transform(res)
          if (picasso.loggingEnabled) {
            Utils.log(Utils.OWNER_HUNTER, Utils.VERB_TRANSFORMED, data.logId(), "from transformations")
          }

          transformedResult
        } catch (e: RuntimeException) {
          Picasso.HANDLER.post {
            throw RuntimeException("Transformation ${transformation.key()} crashed with exception.", e)
          }

          return null
        }

        val bitmap = newResult.bitmap
        if (bitmap.isRecycled) {
          Picasso.HANDLER.post {
            throw IllegalStateException("Transformation ${transformation.key()} returned a recycled Bitmap.")
          }

          return null
        }

        res = newResult
      }

      return res
    }
  }

  val sequence: Int = SEQUENCE_GENERATOR.incrementAndGet()

  var action: Action? = action
    private set

  private var _actions: MutableList<Action>? = null
  val actions: List<Action>?
    get() = _actions

  var future: Future<*>? = null
  var result: RequestHandler.Result? = null
    private set
  var exception: Exception? = null
    private set

  var priority: Picasso.Priority = priority
    private set

  val isCancelled: Boolean
    get() = future?.isCancelled ?: false

  override fun run() {
    try {
      updateThreadName(data)

      if (picasso.loggingEnabled) {
        Utils.log(Utils.OWNER_HUNTER, Utils.VERB_EXECUTING, Utils.getLogIdsForHunter(this))
      }

      result = hunt()
      dispatcher.dispatchComplete(this)
    } catch (e: IOException) {
      exception = e
      dispatcher.dispatchRetry(this)
    } catch (e: Exception) {
      exception = e
      dispatcher.dispatchFailed(this)
    } finally {
      Thread.currentThread().name = Utils.THREAD_IDLE_NAME
    }
  }

  @Throws(IOException::class)
  fun hunt(): RequestHandler.Result.Bitmap? {
    if (shouldReadFromMemoryCache(data.memoryPolicy)) {
      cache[key]?.let { bitmap ->
        picasso.cacheHit()
        if (picasso.loggingEnabled) {
          Utils.log(Utils.OWNER_HUNTER, Utils.VERB_DECODED, data.logId(), "from cache")
        }

        return RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY)
      }
    }

    if (retryCount == 0) {
      data = data.newBuilder().networkPolicy(NetworkPolicy.OFFLINE).build()
    }

    val resultReference = AtomicReference<RequestHandler.Result?>()
    val exceptionReference = AtomicReference<Throwable?>()
    val latch = CountDownLatch(1)
    try {
      requestHandler.load(picasso, data, object : RequestHandler.Callback {
        override fun onSuccess(result: RequestHandler.Result?) {
          resultReference.set(result)
          latch.countDown()
        }

        override fun onError(t: Throwable) {
          exceptionReference.set(t)
          latch.countDown()
        }
      })

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

    val result = resultReference.get() as? RequestHandler.Result.Bitmap
      ?: throw AssertionError("Request handler neither returned a result nor an exception.")
    val bitmap = result.bitmap
    if (picasso.loggingEnabled) {
      Utils.log(Utils.OWNER_HUNTER, Utils.VERB_DECODED, data.logId())
    }
    picasso.bitmapDecoded(bitmap)

    val transformations = ArrayList<Transformation>(data.transformations.size + 1)
    if (data.needsMatrixTransform() || result.exifRotation != 0) {
      transformations.add(MatrixTransformation(data))
    }
    transformations.addAll(data.transformations)

    val transformedResult = applyTransformations(picasso, data, transformations, result) ?: return null
    val transformedBitmap = transformedResult.bitmap
    picasso.bitmapTransformed(transformedBitmap)

    return transformedResult
  }

  fun attach(action: Action) {
    val loggingEnabled = picasso.loggingEnabled
    val request = action.request
    if (this.action == null) {
      this.action = action
      if (loggingEnabled) {
        if (_actions.isNullOrEmpty()) {
          Utils.log(Utils.OWNER_HUNTER, Utils.VERB_JOINED, request.logId(), "to empty hunter")
        } else {
          Utils.log(Utils.OWNER_HUNTER, Utils.VERB_JOINED, request.logId(), Utils.getLogIdsForHunter(this, "to "))
        }
      }

      return
    }

    if (_actions == null) {
      _actions = ArrayList(3)
    }
    _actions?.add(action)

    if (loggingEnabled) {
      Utils.log(Utils.OWNER_HUNTER, Utils.VERB_JOINED, request.logId(), Utils.getLogIdsForHunter(this, "to "))
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
      _actions != null -> {
        _actions?.remove(action) ?: false
      }
      else -> {
        false
      }
    }

    // The action being detached had the highest priority. Update this
    // hunter's priority with the remaining actions.
    if (detached && action.request.priority == priority) {
      priority = computeNewPriority()
    }

    if (picasso.loggingEnabled) {
      Utils.log(Utils.OWNER_HUNTER, Utils.VERB_REMOVED, action.request.logId(), Utils.getLogIdsForHunter(this, "from "))
    }
  }

  fun cancel(): Boolean = action == null && _actions.isNullOrEmpty() && future?.cancel(false) ?: false

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
    var newPriority = Picasso.Priority.LOW

    val hasMultiple = !_actions.isNullOrEmpty()
    val hasAny = action != null || hasMultiple

    // Hunter has no requests, low priority.
    if (!hasAny) {
      return newPriority
    }

    action?.let { action ->
      newPriority = action.request.priority
    }

    _actions?.let { _actions ->
      // Index-based loop to avoid allocating an iterator.
      for (i in 0.._actions.lastIndex) {
        val priority = _actions[i].request.priority
        if (priority.ordinal > newPriority.ordinal) {
          newPriority = priority
        }
      }
    }

    return newPriority
  }
}
