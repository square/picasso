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
package com.example.picasso

import android.content.Context
import android.widget.AbsListView
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL

class SampleScrollListener(private val context: Context) : AbsListView.OnScrollListener {

  override fun onScrollStateChanged(
    view: AbsListView,
    scrollState: Int
  ) {
    val picasso = PicassoInitializer.get()
    when (scrollState) {
      SCROLL_STATE_IDLE, SCROLL_STATE_TOUCH_SCROLL -> picasso.resumeTag(context)
      else -> picasso.pauseTag(context)
    }
  }

  override fun onScroll(
    view: AbsListView,
    firstVisibleItem: Int,
    visibleItemCount: Int,
    totalItemCount: Int
  ) = Unit
}
