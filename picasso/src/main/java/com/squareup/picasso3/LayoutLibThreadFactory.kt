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
import android.os.Handler
import android.os.Message
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ThreadFactory

/**
 * LayoutLib requires assistance bridging the gap to work with Picasso's threads.
 * This workaround ensures the Handler_Delegate executes all threads that are created
 * by Picasso allowing RequestHandler's the chance to fulfill a request synchronously.
 */
@SuppressLint("PrivateApi", "SyntheticAccessor")
internal class LayoutLibThreadFactory : ThreadFactory {

  private lateinit var handlerDelegate: Class<*>
  private lateinit var handlerCallback: Class<*>
  private lateinit var handlerSetCallback: Method
  private lateinit var callbackField: Field
  private lateinit var current: ThreadLocal<*>

  init {
    try {
      handlerDelegate = Class.forName("android.os.Handler_Delegate")
      handlerCallback = Class.forName("android.os.Handler_Delegate\$IHandlerCallback")
      handlerSetCallback = handlerDelegate.getMethod("setCallback", handlerCallback)
      callbackField = handlerDelegate.getDeclaredField("sCallbacks").apply {
        isAccessible = true
      }

      setThreadLocalHandlerCallback()
    } catch (e: ClassNotFoundException) {
      // LayoutLib not in use, no need to workaround
    }
  }

  override fun newThread(runnable: Runnable) =
    object : Thread(runnable) {
      override fun run() {
        if (::handlerDelegate.isInitialized) setThreadLocalHandlerCallback()
        super.run()
        if (::handlerDelegate.isInitialized) unsetThreadLocalHandlerCallback()
      }
    }

  private fun setThreadLocalHandlerCallback() {
    current = callbackField.get(null) as ThreadLocal<*>

    val instance = java.lang.reflect.Proxy.newProxyInstance(
      handlerCallback.classLoader,
      arrayOf(handlerCallback)
    ) { _: Any, method: Method, args: Array<Any> ->
      if (method.name.equals("sendMessageAtTime")) {
        (args[0] as Handler).handleMessage(args[1] as Message)
      }
    }

    handlerSetCallback.invoke(null, instance)
  }

  private fun unsetThreadLocalHandlerCallback() {
    handlerSetCallback.invoke(null, current)
  }
}
