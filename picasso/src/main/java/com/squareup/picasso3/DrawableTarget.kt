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

import android.graphics.drawable.Drawable
import com.squareup.picasso3.Picasso.LoadedFrom

/**
 * Represents an arbitrary listener for image loading.
 *
 * Objects implementing this class **must** have a working implementation of
 * [Object.equals] and [Object.hashCode] for proper storage internally.
 * Instances of this interface will also be compared to determine if view recycling is occurring.
 * It is recommended that you add this interface directly on to a custom view type when using in an
 * adapter to ensure correct recycling behavior.
 */
interface DrawableTarget {
  /**
   * Callback when an image has been successfully loaded.
   *
   */
  fun onDrawableLoaded(
    drawable: Drawable,
    from: LoadedFrom
  )

  /**
   * Callback indicating the image could not be successfully loaded.
   *
   * **Note:** The passed [Drawable] may be `null` if none has been
   * specified via [RequestCreator.error].
   */
  fun onDrawableFailed(
    e: Exception,
    errorDrawable: Drawable?
  )

  /**
   * Callback invoked right before your request is submitted.
   *
   *
   * **Note:** The passed [Drawable] may be `null` if none has been
   * specified via [RequestCreator.placeholder].
   */
  fun onPrepareLoad(placeHolderDrawable: Drawable?)
}
