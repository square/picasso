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

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.ConnectivityManager.CONNECTIVITY_ACTION
import android.net.NetworkInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.util.Log
import androidx.core.content.ContextCompat
import com.squareup.picasso3.BitmapHunter.Companion.forRequest
import com.squareup.picasso3.MemoryPolicy.Companion.shouldWriteToMemoryCache
import com.squareup.picasso3.NetworkPolicy.NO_CACHE
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException
import com.squareup.picasso3.Picasso.Priority.HIGH
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import com.squareup.picasso3.Utils.OWNER_DISPATCHER
import com.squareup.picasso3.Utils.VERB_CANCELED
import com.squareup.picasso3.Utils.VERB_DELIVERED
import com.squareup.picasso3.Utils.VERB_ENQUEUED
import com.squareup.picasso3.Utils.VERB_IGNORED
import com.squareup.picasso3.Utils.VERB_PAUSED
import com.squareup.picasso3.Utils.VERB_REPLAYING
import com.squareup.picasso3.Utils.VERB_RETRYING
import com.squareup.picasso3.Utils.flushStackLocalLeaks
import com.squareup.picasso3.Utils.getLogIdsForHunter
import com.squareup.picasso3.Utils.hasPermission
import com.squareup.picasso3.Utils.isAirplaneModeOn
import com.squareup.picasso3.Utils.log
import java.util.concurrent.ExecutorService

internal class Dispatcher internal constructor(
  private val context: Context,
  @get:JvmName("-service") internal val service: ExecutorService,
  private val mainThreadHandler: Handler,
  private val cache: PlatformLruCache
) {
  @get:JvmName("-hunterMap")
  internal val hunterMap = mutableMapOf<String, BitmapHunter>()

  @get:JvmName("-failedActions")
  internal val failedActions = mutableMapOf<Any, Action>()

  @get:JvmName("-pausedActions")
  internal val pausedActions = mutableMapOf<Any, Action>()

  @get:JvmName("-pausedTags")
  internal val pausedTags = mutableSetOf<Any>()

  @get:JvmName("-receiver")
  internal val receiver: NetworkBroadcastReceiver

  @get:JvmName("-airplaneMode")
  @set:JvmName("-airplaneMode")
  internal var airplaneMode = isAirplaneModeOn(context)

  private val dispatcherThread: DispatcherThread
  private val handler: Handler
  private val scansNetworkChanges: Boolean

  init {
    dispatcherThread = DispatcherThread()
    dispatcherThread.start()
    val dispatcherThreadLooper = dispatcherThread.looper
    flushStackLocalLeaks(dispatcherThreadLooper)
    handler = DispatcherHandler(dispatcherThreadLooper, this)
    scansNetworkChanges = hasPermission(context, ACCESS_NETWORK_STATE)
    receiver = NetworkBroadcastReceiver(this)
    receiver.register()
  }

  fun shutdown() {
    // Shutdown the thread pool only if it is the one created by Picasso.
    (service as? PicassoExecutorService)?.shutdown()
    dispatcherThread.quit()
    // Unregister network broadcast receiver on the main thread.
    Picasso.HANDLER.post { receiver.unregister() }
  }

  fun dispatchSubmit(action: Action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, action))
  }

  fun dispatchCancel(action: Action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_CANCEL, action))
  }

  fun dispatchPauseTag(tag: Any) {
    handler.sendMessage(handler.obtainMessage(TAG_PAUSE, tag))
  }

  fun dispatchResumeTag(tag: Any) {
    handler.sendMessage(handler.obtainMessage(TAG_RESUME, tag))
  }

  fun dispatchComplete(hunter: BitmapHunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_COMPLETE, hunter))
  }

  fun dispatchRetry(hunter: BitmapHunter) {
    handler.sendMessageDelayed(handler.obtainMessage(HUNTER_RETRY, hunter), RETRY_DELAY)
  }

  fun dispatchFailed(hunter: BitmapHunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_DECODE_FAILED, hunter))
  }

  fun dispatchNetworkStateChange(info: NetworkInfo) {
    handler.sendMessage(handler.obtainMessage(NETWORK_STATE_CHANGE, info))
  }

  fun dispatchAirplaneModeChange(airplaneMode: Boolean) {
    handler.sendMessage(
      handler.obtainMessage(
        AIRPLANE_MODE_CHANGE,
        if (airplaneMode) AIRPLANE_MODE_ON else AIRPLANE_MODE_OFF,
        0
      )
    )
  }

  fun performSubmit(action: Action, dismissFailed: Boolean = true) {
    if (action.tag in pausedTags) {
      pausedActions[action.getTarget()] = action
      if (action.picasso.isLoggingEnabled) {
        log(
          owner = OWNER_DISPATCHER,
          verb = VERB_PAUSED,
          logId = action.request.logId(),
          extras = "because tag '${action.tag}' is paused"
        )
      }
      return
    }

    var hunter = hunterMap[action.request.key]
    if (hunter != null) {
      hunter.attach(action)
      return
    }

    if (service.isShutdown) {
      if (action.picasso.isLoggingEnabled) {
        log(
          owner = OWNER_DISPATCHER,
          verb = VERB_IGNORED,
          logId = action.request.logId(),
          extras = "because shut down"
        )
      }
      return
    }

    hunter = forRequest(action.picasso, this, cache, action)
    hunter.future = service.submit(hunter)
    hunterMap[action.request.key] = hunter
    if (dismissFailed) {
      failedActions.remove(action.getTarget())
    }

    if (action.picasso.isLoggingEnabled) {
      log(owner = OWNER_DISPATCHER, verb = VERB_ENQUEUED, logId = action.request.logId())
    }
  }

  fun performCancel(action: Action) {
    val key = action.request.key
    val hunter = hunterMap[key]
    if (hunter != null) {
      hunter.detach(action)
      if (hunter.cancel()) {
        hunterMap.remove(key)
        if (action.picasso.isLoggingEnabled) {
          log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId())
        }
      }
    }

    if (action.tag in pausedTags) {
      pausedActions.remove(action.getTarget())
      if (action.picasso.isLoggingEnabled) {
        log(
          owner = OWNER_DISPATCHER,
          verb = VERB_CANCELED,
          logId = action.request.logId(),
          extras = "because paused request got canceled"
        )
      }
    }

    val remove = failedActions.remove(action.getTarget())
    if (remove != null && remove.picasso.isLoggingEnabled) {
      log(OWNER_DISPATCHER, VERB_CANCELED, remove.request.logId(), "from replaying")
    }
  }

  fun performPauseTag(tag: Any) {
    // Trying to pause a tag that is already paused.
    if (!pausedTags.add(tag)) {
      return
    }

    // Go through all active hunters and detach/pause the requests
    // that have the paused tag.
    val iterator = hunterMap.values.iterator()
    while (iterator.hasNext()) {
      val hunter = iterator.next()
      val loggingEnabled = hunter.picasso.isLoggingEnabled

      val single = hunter.action
      val joined = hunter.actions
      val hasMultiple = !joined.isNullOrEmpty()

      // Hunter has no requests, bail early.
      if (single == null && !hasMultiple) {
        continue
      }

      if (single != null && single.tag == tag) {
        hunter.detach(single)
        pausedActions[single.getTarget()] = single
        if (loggingEnabled) {
          log(
            owner = OWNER_DISPATCHER,
            verb = VERB_PAUSED,
            logId = single.request.logId(),
            extras = "because tag '$tag' was paused"
          )
        }
      }

      if (joined != null) {
        for (i in joined.indices.reversed()) {
          val action = joined[i]
          if (action.tag != tag) {
            continue
          }
          hunter.detach(action)
          pausedActions[action.getTarget()] = action
          if (loggingEnabled) {
            log(
              owner = OWNER_DISPATCHER,
              verb = VERB_PAUSED,
              logId = action.request.logId(),
              extras = "because tag '$tag' was paused"
            )
          }
        }
      }

      // Check if the hunter can be cancelled in case all its requests
      // had the tag being paused here.
      if (hunter.cancel()) {
        iterator.remove()
        if (loggingEnabled) {
          log(
            owner = OWNER_DISPATCHER,
            verb = VERB_CANCELED,
            logId = getLogIdsForHunter(hunter),
            extras = "all actions paused"
          )
        }
      }
    }
  }

  fun performResumeTag(tag: Any) {
    // Trying to resume a tag that is not paused.
    if (!pausedTags.remove(tag)) {
      return
    }

    val batch = mutableListOf<Action>()
    val iterator = pausedActions.values.iterator()
    while (iterator.hasNext()) {
      val action = iterator.next()
      if (action.tag == tag) {
        batch += action
        iterator.remove()
      }
    }

    if (batch.isNotEmpty()) {
      mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(REQUEST_BATCH_RESUME, batch))
    }
  }

  @SuppressLint("MissingPermission")
  fun performRetry(hunter: BitmapHunter) {
    if (hunter.isCancelled) return

    if (service.isShutdown) {
      performError(hunter)
      return
    }

    var networkInfo: NetworkInfo? = null
    if (scansNetworkChanges) {
      val connectivityManager =
        ContextCompat.getSystemService(context, ConnectivityManager::class.java)
      if (connectivityManager != null) {
        networkInfo = connectivityManager.activeNetworkInfo
      }
    }

    if (hunter.shouldRetry(airplaneMode, networkInfo)) {
      if (hunter.picasso.isLoggingEnabled) {
        log(
          owner = OWNER_DISPATCHER,
          verb = VERB_RETRYING,
          logId = getLogIdsForHunter(hunter)
        )
      }
      if (hunter.exception is ContentLengthException) {
        hunter.data = hunter.data.newBuilder().networkPolicy(NO_CACHE).build()
      }
      hunter.future = service.submit(hunter)
    } else {
      performError(hunter)
      // Mark for replay only if we observe network info changes and support replay.
      if (scansNetworkChanges && hunter.supportsReplay()) {
        markForReplay(hunter)
      }
    }
  }

  fun performComplete(hunter: BitmapHunter) {
    if (shouldWriteToMemoryCache(hunter.data.memoryPolicy)) {
      val result = hunter.result
      if (result != null) {
        if (result is Bitmap) {
          val bitmap = result.bitmap
          cache[hunter.key] = bitmap
        }
      }
    }
    hunterMap.remove(hunter.key)
    deliver(hunter)
  }

  fun performError(hunter: BitmapHunter) {
    hunterMap.remove(hunter.key)
    deliver(hunter)
  }

  fun performAirplaneModeChange(airplaneMode: Boolean) {
    this.airplaneMode = airplaneMode
  }

  fun performNetworkStateChange(info: NetworkInfo?) {
    // Intentionally check only if isConnected() here before we flush out failed actions.
    if (info != null && info.isConnected) {
      flushFailedActions()
    }
  }

  private fun flushFailedActions() {
    if (failedActions.isNotEmpty()) {
      val iterator = failedActions.values.iterator()
      while (iterator.hasNext()) {
        val action = iterator.next()
        iterator.remove()
        if (action.picasso.isLoggingEnabled) {
          log(
            owner = OWNER_DISPATCHER,
            verb = VERB_REPLAYING,
            logId = action.request.logId()
          )
        }
        performSubmit(action, false)
      }
    }
  }

  private fun markForReplay(hunter: BitmapHunter) {
    val action = hunter.action
    action?.let { markForReplay(it) }
    val joined = hunter.actions
    if (joined != null) {
      for (i in joined.indices) {
        markForReplay(joined[i])
      }
    }
  }

  private fun markForReplay(action: Action) {
    val target = action.getTarget()
    action.willReplay = true
    failedActions[target] = action
  }

  private fun deliver(hunter: BitmapHunter) {
    if (hunter.isCancelled) {
      return
    }
    val result = hunter.result
    if (result != null) {
      if (result is Bitmap) {
        val bitmap = result.bitmap
        bitmap.prepareToDraw()
      }
    }

    val message = mainThreadHandler.obtainMessage(HUNTER_COMPLETE, hunter)
    if (hunter.priority == HIGH) {
      mainThreadHandler.sendMessageAtFrontOfQueue(message)
    } else {
      mainThreadHandler.sendMessage(message)
    }
    logDelivery(hunter)
  }

  private fun logDelivery(bitmapHunter: BitmapHunter) {
    val picasso = bitmapHunter.picasso
    if (picasso.isLoggingEnabled) {
      log(
        owner = OWNER_DISPATCHER,
        verb = VERB_DELIVERED,
        logId = getLogIdsForHunter(bitmapHunter)
      )
    }
  }

  private class DispatcherHandler(
    looper: Looper,
    private val dispatcher: Dispatcher
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
          Picasso.HANDLER.post {
            throw AssertionError("Unknown handler message received: ${msg.what}")
          }
        }
      }
    }
  }

  internal class DispatcherThread : HandlerThread(
    Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME,
    THREAD_PRIORITY_BACKGROUND
  )

  internal class NetworkBroadcastReceiver(
    private val dispatcher: Dispatcher
  ) : BroadcastReceiver() {
    fun register() {
      val filter = IntentFilter()
      filter.addAction(ACTION_AIRPLANE_MODE_CHANGED)
      if (dispatcher.scansNetworkChanges) {
        filter.addAction(CONNECTIVITY_ACTION)
      }
      dispatcher.context.registerReceiver(this, filter)
    }

    fun unregister() {
      dispatcher.context.unregisterReceiver(this)
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent?) {
      // On some versions of Android this may be called with a null Intent,
      // also without extras (getExtras() == null), in such case we use defaults.
      if (intent == null) {
        return
      }
      when (intent.action) {
        ACTION_AIRPLANE_MODE_CHANGED -> {
          if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
            return // No airplane state, ignore it. Should we query Utils.isAirplaneModeOn?
          }
          dispatcher.dispatchAirplaneModeChange(intent.getBooleanExtra(EXTRA_AIRPLANE_STATE, false))
        }
        CONNECTIVITY_ACTION -> {
          val connectivityManager =
            ContextCompat.getSystemService(context, ConnectivityManager::class.java)
          val networkInfo = try {
            connectivityManager!!.activeNetworkInfo
          } catch (re: RuntimeException) {
            Log.w(TAG, "System UI crashed, ignoring attempt to change network state.")
            return
          }
          if (networkInfo == null) {
            Log.w(
              TAG,
              "No default network is currently active, ignoring attempt to change network state."
            )
            return
          }
          dispatcher.dispatchNetworkStateChange(networkInfo)
        }
      }
    }

    internal companion object {
      const val EXTRA_AIRPLANE_STATE = "state"
    }
  }

  internal companion object {
    private const val RETRY_DELAY = 500L
    private const val AIRPLANE_MODE_ON = 1
    private const val AIRPLANE_MODE_OFF = 0
    private const val REQUEST_SUBMIT = 1
    private const val REQUEST_CANCEL = 2
    const val HUNTER_COMPLETE = 4
    private const val HUNTER_RETRY = 5
    private const val HUNTER_DECODE_FAILED = 6
    const val NETWORK_STATE_CHANGE = 9
    private const val AIRPLANE_MODE_CHANGE = 10
    private const val TAG_PAUSE = 11
    private const val TAG_RESUME = 12
    const val REQUEST_BATCH_RESUME = 13
    private const val DISPATCHER_THREAD_NAME = "Dispatcher"
  }
}
