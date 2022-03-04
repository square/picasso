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
package com.example.picasso.provider

import com.squareup.picasso3.Picasso
import com.squareup.picasso3.stats.StatsEventListener

/**
 * This instance is automatically initialized with defaults that are suitable to most
 * implementations.
 *
 *  * LRU memory cache of 15% the available application RAM
 *  * Disk cache of 2% storage space up to 50MB but no less than 5MB. (Note: this is only
 * available on API 14+ *or* if you are using a standalone library that provides a disk
 * cache on all API levels like OkHttp)
 *  * Three download threads for disk and network access.
 *
 * If these settings do not meet the requirements of your application, you can construct your own
 * with full control over the configuration by using [Picasso.Builder] to create a
 * customized [Picasso] instance.
 *
 * Note: Production apps could instead use dependency injection to provide their Picasso instances.
 */
object PicassoProvider {
  private val instance: Picasso by lazy {
    Picasso
      .Builder(PicassoContentProvider.autoContext!!)
      .addEventListener(StatsEventListener())
      .build()
  }

  fun get() = instance
}
