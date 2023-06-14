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
import java.util.concurrent.ExecutorService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class InternalCoroutineDispatcher internal constructor(
  context: Context,
  service: ExecutorService,
  mainThreadHandler: Handler,
  cache: PlatformLruCache,
  val picassoDispatcher: CoroutineDispatcher
) : Dispatcher(context, service, mainThreadHandler, cache) {

  @OptIn(ExperimentalCoroutinesApi::class)
  private val scope = CoroutineScope(picassoDispatcher.limitedParallelism(1))

  override fun shutdown() {
    super.shutdown()
    scope.cancel()
  }

  override fun dispatchSubmit(action: Action) {
    scope.launch {
      performSubmit(action)
    }
  }

  override fun dispatchCancel(action: Action) {
    scope.launch {
      performCancel(action)
    }
  }

  override fun dispatchPauseTag(tag: Any) {
    scope.launch {
      performPauseTag(tag)
    }
  }

  override fun dispatchResumeTag(tag: Any) {
    scope.launch {
      performResumeTag(tag)
    }
  }

  override fun dispatchComplete(hunter: BitmapHunter) {
    scope.launch {
      performComplete(hunter)
    }
  }

  override fun dispatchRetry(hunter: BitmapHunter) {
    scope.launch {
      delay(RETRY_DELAY)
      performRetry(hunter)
    }
  }

  override fun dispatchFailed(hunter: BitmapHunter) {
    scope.launch {
      performError(hunter)
    }
  }

  override fun dispatchNetworkStateChange(info: NetworkInfo) {
    scope.launch {
      performNetworkStateChange(info)
    }
  }

  override fun dispatchAirplaneModeChange(airplaneMode: Boolean) {
    scope.launch {
      performAirplaneModeChange(airplaneMode)
    }
  }

  override fun dispatchCompleteMain(hunter: BitmapHunter) {
    scope.launch(Dispatchers.Main) {
      performCompleteMain(hunter)
    }
  }

  override fun dispatchBatchResumeMain(batch: MutableList<Action>) {
    scope.launch(Dispatchers.Main) {
      performBatchResumeMain(batch)
    }
  }
}
