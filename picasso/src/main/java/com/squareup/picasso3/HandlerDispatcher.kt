/*
 * Copyright (C) 2023 Square, Inc.
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

import android.content.Context
import android.net.NetworkInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import com.squareup.picasso3.Picasso.Priority.HIGH
import com.squareup.picasso3.Utils.flushStackLocalLeaks
import java.util.concurrent.ExecutorService

internal class HandlerDispatcher internal constructor(
  context: Context,
  @get:JvmName("-service") val service: ExecutorService,
  mainThreadHandler: Handler,
  cache: PlatformLruCache
) : BaseDispatcher(context, mainThreadHandler, cache) {

  private val dispatcherThread: DispatcherThread
  private val handler: Handler
  private val mainHandler: Handler

  init {
    dispatcherThread = DispatcherThread()
    dispatcherThread.start()
    val dispatcherThreadLooper = dispatcherThread.looper
    flushStackLocalLeaks(dispatcherThreadLooper)
    handler = DispatcherHandler(dispatcherThreadLooper, this)
    mainHandler = MainDispatcherHandler(mainThreadHandler.looper, this)
  }

  override fun shutdown() {
    super.shutdown()
    // Shutdown the thread pool only if it is the one created by Picasso.
    (service as? PicassoExecutorService)?.shutdown()

    dispatcherThread.quit()
  }

  override fun dispatchSubmit(action: Action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, action))
  }

  override fun dispatchCancel(action: Action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_CANCEL, action))
  }

  override fun dispatchPauseTag(tag: Any) {
    handler.sendMessage(handler.obtainMessage(TAG_PAUSE, tag))
  }

  override fun dispatchResumeTag(tag: Any) {
    handler.sendMessage(handler.obtainMessage(TAG_RESUME, tag))
  }

  override fun dispatchComplete(hunter: BitmapHunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_COMPLETE, hunter))
  }

  override fun dispatchRetry(hunter: BitmapHunter) {
    handler.sendMessageDelayed(handler.obtainMessage(HUNTER_RETRY, hunter), RETRY_DELAY)
  }

  override fun dispatchFailed(hunter: BitmapHunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_DECODE_FAILED, hunter))
  }

  override fun dispatchNetworkStateChange(info: NetworkInfo) {
    handler.sendMessage(handler.obtainMessage(NETWORK_STATE_CHANGE, info))
  }

  override fun dispatchAirplaneModeChange(airplaneMode: Boolean) {
    handler.sendMessage(
      handler.obtainMessage(
        AIRPLANE_MODE_CHANGE,
        if (airplaneMode) AIRPLANE_MODE_ON else AIRPLANE_MODE_OFF,
        0
      )
    )
  }

  override fun dispatchSubmit(hunter: BitmapHunter) {
    hunter.future = service.submit(hunter)
  }

  override fun dispatchCompleteMain(hunter: BitmapHunter) {
    val message = mainHandler.obtainMessage(HUNTER_COMPLETE, hunter)
    if (hunter.priority == HIGH) {
      mainHandler.sendMessageAtFrontOfQueue(message)
    } else {
      mainHandler.sendMessage(message)
    }
  }

  override fun dispatchBatchResumeMain(batch: MutableList<Action>) {
    mainHandler.sendMessage(mainHandler.obtainMessage(REQUEST_BATCH_RESUME, batch))
  }
  override fun isShutdown() = service.isShutdown

  private class DispatcherHandler(
    looper: Looper,
    private val dispatcher: HandlerDispatcher
  ) : Handler(looper) {
    override fun handleMessage(msg: Message) {
      when (msg.what) {
        REQUEST_SUBMIT -> {
          val action = msg.obj as Action
          dispatcher.performSubmit(action)
        }
        REQUEST_CANCEL -> {
          val action = msg.obj as Action
          dispatcher.performCancel(action)
        }
        TAG_PAUSE -> {
          val tag = msg.obj
          dispatcher.performPauseTag(tag)
        }
        TAG_RESUME -> {
          val tag = msg.obj
          dispatcher.performResumeTag(tag)
        }
        HUNTER_COMPLETE -> {
          val hunter = msg.obj as BitmapHunter
          dispatcher.performComplete(hunter)
        }
        HUNTER_RETRY -> {
          val hunter = msg.obj as BitmapHunter
          dispatcher.performRetry(hunter)
        }
        HUNTER_DECODE_FAILED -> {
          val hunter = msg.obj as BitmapHunter
          dispatcher.performError(hunter)
        }
        NETWORK_STATE_CHANGE -> {
          val info = msg.obj as NetworkInfo
          dispatcher.performNetworkStateChange(info)
        }
        AIRPLANE_MODE_CHANGE -> {
          dispatcher.performAirplaneModeChange(msg.arg1 == AIRPLANE_MODE_ON)
        }
        else -> {
          dispatcher.mainHandler.post {
            throw AssertionError("Unknown handler message received: ${msg.what}")
          }
        }
      }
    }
  }

  private class MainDispatcherHandler(
    looper: Looper,
    val dispatcher: HandlerDispatcher
  ) : Handler(looper) {
    override fun handleMessage(msg: Message) {
      when (msg.what) {
        HUNTER_COMPLETE -> {
          val hunter = msg.obj as BitmapHunter
          dispatcher.performCompleteMain(hunter)
        }
        REQUEST_BATCH_RESUME -> {
          val batch = msg.obj as List<Action>
          dispatcher.performBatchResumeMain(batch)
        }
        else -> throw AssertionError("Unknown handler message received: " + msg.what)
      }
    }
  }

  internal class DispatcherThread : HandlerThread(
    Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME,
    THREAD_PRIORITY_BACKGROUND
  )
  internal companion object {
    private const val RETRY_DELAY = 500L
    private const val AIRPLANE_MODE_ON = 1
    private const val AIRPLANE_MODE_OFF = 0
    private const val REQUEST_SUBMIT = 1
    private const val REQUEST_CANCEL = 2
    private const val HUNTER_COMPLETE = 4
    private const val HUNTER_RETRY = 5
    private const val HUNTER_DECODE_FAILED = 6
    private const val NETWORK_STATE_CHANGE = 9
    private const val AIRPLANE_MODE_CHANGE = 10
    private const val TAG_PAUSE = 11
    private const val TAG_RESUME = 12
    private const val REQUEST_BATCH_RESUME = 13
    private const val DISPATCHER_THREAD_NAME = "Dispatcher"
  }
}
