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
import androidx.startup.Initializer
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.stats.StatsEventListener

class PicassoInitializer : Initializer<Unit> {
  override fun create(context: Context) {
    appContext = context
  }

  override fun dependencies() = emptyList<Class<Initializer<*>>>()

  companion object {
    private lateinit var appContext: Context
    private val instance: Picasso by lazy {
      Picasso
        .Builder(appContext)
        .addEventListener(StatsEventListener())
        .build()
    }
    fun get() = instance
  }
}
