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

import android.annotation.SuppressLint
import android.os.Handler_Delegate
import java.util.concurrent.ThreadFactory

/**
 * LayoutLib requires assistance bridging the gap to work with ThreadFactory.
 * This workaround ensures the Handler_Delegate executes all threads that are created
 * by Picasso allowing RequestHandler's the chance to fulfill a request synchronously.
 */
@SuppressLint("SyntheticAccessor")
class LayoutLibThreadFactory : ThreadFactory {

  init {
    if (layoutLibEnvironment) {
      // Handler_Delegate sendMessageAtTime drops messages that
      // don't contain a callback. So attach a callback which
      // executes all messages
      Handler_Delegate.setCallback { handler, message, _ -> handler.handleMessage(message) }
    }
  }

  override fun newThread(runnable: Runnable) =
    object : Thread(runnable) {
      override fun run() {
        // Handler_Delegate's callback is ThreadLocal, so we also need
        // to handle messages for Picasso's Threads.
        if (layoutLibEnvironment)
          Handler_Delegate.setCallback { handler, message, _ -> handler.handleMessage(message) }

        super.run()
      }
    }

  private companion object {
    private val layoutLibEnvironment by lazy {
      try {
        // When deploying an @Preview on a device we are not using
        // layoutlib and will crash due to a ClassNotFoundException
        // exception.
        Class.forName("android.os.Handler_Delegate")
        true
      } catch (e: ClassNotFoundException) {
        false
      }
    }
  }
}
