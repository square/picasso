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
import android.util.Log
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.squareup.picasso3.BitmapHunter.Companion.forRequest
import com.squareup.picasso3.MemoryPolicy.Companion.shouldWriteToMemoryCache
import com.squareup.picasso3.NetworkPolicy.NO_CACHE
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import com.squareup.picasso3.Utils.OWNER_DISPATCHER
import com.squareup.picasso3.Utils.VERB_CANCELED
import com.squareup.picasso3.Utils.VERB_DELIVERED
import com.squareup.picasso3.Utils.VERB_ENQUEUED
import com.squareup.picasso3.Utils.VERB_IGNORED
import com.squareup.picasso3.Utils.VERB_PAUSED
import com.squareup.picasso3.Utils.VERB_REPLAYING
import com.squareup.picasso3.Utils.VERB_RETRYING
import com.squareup.picasso3.Utils.getLogIdsForHunter
import com.squareup.picasso3.Utils.hasPermission
import com.squareup.picasso3.Utils.isAirplaneModeOn
import com.squareup.picasso3.Utils.log
import java.util.WeakHashMap

internal abstract class BaseDispatcher internal constructor(
  private val context: Context,
  private val mainThreadHandler: Handler,
  private val cache: PlatformLruCache
) : Dispatcher {
  @get:JvmName("-hunterMap")
  internal val hunterMap = mutableMapOf<String, BitmapHunter>()

  @get:JvmName("-failedActions")
  internal val failedActions = WeakHashMap<Any, Action>()

  @get:JvmName("-pausedActions")
  internal val pausedActions = WeakHashMap<Any, Action>()

  @get:JvmName("-pausedTags")
  internal val pausedTags = mutableSetOf<Any>()

  @get:JvmName("-receiver")
  internal val receiver: NetworkBroadcastReceiver

  @get:JvmName("-airplaneMode")
  @set:JvmName("-airplaneMode")
  internal var airplaneMode = isAirplaneModeOn(context)

  private val scansNetworkChanges: Boolean

  init {
    scansNetworkChanges = hasPermission(context, ACCESS_NETWORK_STATE)
    receiver = NetworkBroadcastReceiver(this)
    receiver.register()
  }

  @CallSuper override fun shutdown() {
    // Unregister network broadcast receiver on the main thread.
    mainThreadHandler.post { receiver.unregister() }
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

    if (isShutdown()) {
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
    dispatchSubmit(hunter)
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
      dispatchBatchResumeMain(batch)
    }
  }

  @SuppressLint("MissingPermission")
  fun performRetry(hunter: BitmapHunter) {
    if (hunter.isCancelled) return

    if (isShutdown()) {
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
      dispatchSubmit(hunter)
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

  @MainThread
  fun performCompleteMain(hunter: BitmapHunter) {
    hunter.picasso.complete(hunter)
  }

  @MainThread
  fun performBatchResumeMain(batch: List<Action>) {
    for (i in batch.indices) {
      val action = batch[i]
      action.picasso.resumeAction(action)
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
    hunter.action?.let { markForReplay(it) }
    hunter.actions?.forEach { markForReplay(it) }
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

    dispatchCompleteMain(hunter)
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

  internal class NetworkBroadcastReceiver(
    private val dispatcher: BaseDispatcher
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
}
