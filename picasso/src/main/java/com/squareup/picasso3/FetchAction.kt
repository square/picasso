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

import com.squareup.picasso3.RequestHandler.Result

internal class FetchAction(
  picasso: Picasso,
  data: Request,
  private var callback: Callback?
) : Action(picasso, data) {
  override fun complete(result: Result) {
    callback?.onSuccess()
  }

  override fun error(e: Exception) {
    callback?.onError(e)
  }

  override fun getTarget() = this

  override fun cancel() {
    super.cancel()
    callback = null
  }
}
