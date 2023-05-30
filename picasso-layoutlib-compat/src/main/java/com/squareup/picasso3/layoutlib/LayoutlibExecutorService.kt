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
package com.squareup.picasso3.layoutlib

import android.os.Handler_Delegate
import com.android.layoutlib.bridge.impl.RenderAction
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * Keeps Picasso single threaded and assist's Layoutlib by executing our
 * Handler messages. This workaround ensures Handler_Delegate executes
 * all messages that are created by Picasso, allowing RequestHandlers the
 * chance to fulfill a request synchronously.
 */
class LayoutlibExecutorService : AbstractExecutorService() {

  private var isShutdown = false

  init {
    if (isLayoutlibPresent) {
      // Handler_Delegate.sendMessageAtTime drops messages without a callback.
      Handler_Delegate.setCallback { handler, message, uptimeMillis ->
        if (message.callback != null) {
          // Default implementation sends Messages with a callback to this HandlerMessageQueue
          // https://cs.android.com/android/platform/superproject/+/master:frameworks/layoutlib/bridge/src/android/os/Handler_Delegate.java;l=44;bpv=1;bpt=0
          val context = RenderAction.getCurrentContext()
          context.sessionInteractiveData.handlerMessageQueue.add(
            handler,
            uptimeMillis,
            message.callback
          )
        } else if (MESSAGE_OBJECTS.find { it.isInstance(message.obj) } != null) {
          // This is a Picasso Message trying to be enqueued, so we forward it to continue processing
          handler.handleMessage(message)
        }
      }
    }
  }

  override fun shutdown() {
    isShutdown = true
  }

  @Throws(InterruptedException::class)
  override fun awaitTermination(
    theTimeout: Long,
    theUnit: TimeUnit
  ): Boolean {
    shutdown()
    return isShutdown
  }

  override fun shutdownNow() = Collections.emptyList<Runnable>()
  override fun isShutdown() = isShutdown
  override fun isTerminated() = isShutdown
  override fun execute(runnable: Runnable) = runnable.run()

  private companion object {
    private val MESSAGE_OBJECTS = listOf(
      Class.forName("com.squareup.picasso3.Action"),
      Class.forName("com.squareup.picasso3.BitmapHunter")
    )

    private val isLayoutlibPresent by lazy {
      try {
        // Emulators/devices don't load layoutlib classes, so deploying a
        // @Preview will otherwise crash with a ClassNotFoundException
        Class.forName("android.os.Handler_Delegate")
        true
      } catch (e: ClassNotFoundException) {
        false
      }
    }
  }
}
