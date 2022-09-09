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
package com.squareup.picasso3.compose

import android.view.Gravity
import androidx.compose.ui.Alignment

internal fun Alignment.toGravity(): Int =
  when (this) {
    Alignment.TopStart -> Gravity.TOP or Gravity.START
    Alignment.TopCenter -> Gravity.TOP or Gravity.CENTER
    Alignment.TopEnd -> Gravity.TOP or Gravity.END
    Alignment.CenterStart -> Gravity.CENTER or Gravity.START
    Alignment.Center -> Gravity.CENTER
    Alignment.CenterEnd -> Gravity.CENTER or Gravity.END
    Alignment.BottomStart -> Gravity.BOTTOM or Gravity.START
    Alignment.BottomCenter -> Gravity.BOTTOM or Gravity.CENTER
    Alignment.BottomEnd -> Gravity.BOTTOM or Gravity.END
    else -> Gravity.CENTER
  }
