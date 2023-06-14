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

internal interface Dispatcher {
  fun shutdown()

  fun dispatchSubmit(action: Action)

  fun dispatchCancel(action: Action)

  fun dispatchPauseTag(tag: Any)

  fun dispatchResumeTag(tag: Any)

  fun dispatchComplete(hunter: BitmapHunter)

  fun dispatchRetry(hunter: BitmapHunter)

  fun dispatchFailed(hunter: BitmapHunter)

  fun dispatchNetworkStateChange(info: NetworkInfo)

  fun dispatchAirplaneModeChange(airplaneMode: Boolean)

  fun dispatchSubmit(hunter: BitmapHunter)

  fun dispatchCompleteMain(hunter: BitmapHunter)

  fun dispatchBatchResumeMain(batch: MutableList<Action>)

  fun isShutdown(): Boolean

  companion object {
    const val RETRY_DELAY = 500L
  }
}
