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
import java.util.concurrent.ThreadFactory

/**
 * LayoutLib requires assistance working with Handler Threads.
 * This workaround ensures Handler_Delegate executes all threads that are created
 * by Picasso, allowing RequestHandlers the chance to fulfill a request synchronously.
 */
class LayoutlibThreadFactory : ThreadFactory {
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
        } else if (ACTION.isInstance(message.obj)) {
          // This message is a Picasso Action trying to be enqueued, so we forward it to continue processing
          handler.handleMessage(message)
        }
      }
    }
  }

  override fun newThread(runnable: Runnable) =
    object : Thread(runnable) {
      override fun run() {
        // Handler_Delegate stores thread-local callbacks, so this callback is for Picasso Executor Thread message handling
        if (isLayoutlibPresent) {
          Handler_Delegate.setCallback { handler, message, _ -> handler.handleMessage(message) }
        }
        super.run()
      }
    }

  private companion object {
    private val ACTION = Class.forName("com.squareup.picasso3.Action")
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
