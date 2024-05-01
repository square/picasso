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
import com.squareup.picasso3.Dispatcher.Companion.RETRY_DELAY
import com.squareup.picasso3.Picasso.Priority.HIGH
import com.squareup.picasso3.Utils.OWNER_DISPATCHER
import com.squareup.picasso3.Utils.VERB_CANCELED
import com.squareup.picasso3.Utils.log
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class InternalCoroutineDispatcher internal constructor(
  context: Context,
  mainThreadHandler: Handler,
  cache: PlatformLruCache,
  val mainContext: CoroutineContext,
  val backgroundContext: CoroutineContext
) : BaseDispatcher(context, mainThreadHandler, cache) {

  private val scope = CoroutineScope(SupervisorJob() + backgroundContext)
  private val channel = Channel<() -> Unit>(capacity = Channel.UNLIMITED)

  init {
    // Using a channel to enforce sequential access for this class' internal state
    scope.launch {
      channel.receiveAsFlow().collect {
        it.invoke()
      }
    }
  }

  override fun shutdown() {
    super.shutdown()
    channel.close()
    scope.cancel()
  }

  override fun dispatchSubmit(action: Action) {
    channel.trySend {
      performSubmit(action)
    }
  }

  override fun dispatchCancel(action: Action) {
    channel.trySend {
      performCancel(action)
    }
  }

  override fun dispatchPauseTag(tag: Any) {
    channel.trySend {
      performPauseTag(tag)
    }
  }

  override fun dispatchResumeTag(tag: Any) {
    channel.trySend {
      performResumeTag(tag)
    }
  }

  override fun dispatchComplete(hunter: BitmapHunter) {
    channel.trySend {
      performComplete(hunter)
    }
  }

  override fun dispatchRetry(hunter: BitmapHunter) {
    scope.launch {
      delay(RETRY_DELAY)
      channel.send {
        performRetry(hunter)
      }
    }
  }

  override fun dispatchFailed(hunter: BitmapHunter) {
    channel.trySend {
      performError(hunter)
    }
  }

  override fun dispatchNetworkStateChange(info: NetworkInfo) {
    channel.trySend {
      performNetworkStateChange(info)
    }
  }

  override fun dispatchAirplaneModeChange(airplaneMode: Boolean) {
    channel.trySend {
      performAirplaneModeChange(airplaneMode)
    }
  }

  override fun dispatchCompleteMain(hunter: BitmapHunter) {
    scope.launch(mainContext) {
      performCompleteMain(hunter)
    }
  }

  override fun dispatchBatchResumeMain(batch: MutableList<Action>) {
    scope.launch(mainContext) {
      performBatchResumeMain(batch)
    }
  }

  override fun dispatchSubmit(hunter: BitmapHunter) {
    val highPriority = hunter.action?.request?.priority == HIGH
    val context = if (highPriority) EmptyCoroutineContext else mainContext

    scope.launch(context) {
      channel.trySend {
        if (hunter.action != null) {
          hunter.job = scope.launch(CoroutineName(hunter.getName())) {
            hunter.run()
          }
        } else {
          hunterMap.remove(hunter.key)
          if (hunter.picasso.isLoggingEnabled) {
            log(OWNER_DISPATCHER, VERB_CANCELED, hunter.key)
          }
        }
      }
    }
  }

  override fun isShutdown() = !scope.isActive
}
