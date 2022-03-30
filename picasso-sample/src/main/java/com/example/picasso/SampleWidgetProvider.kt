/*
 * Copyright (C) 2014 Square, Inc.
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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.util.Random

class SampleWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    val updateViews = RemoteViews(context.packageName, R.layout.sample_widget)

    // Load image for all appWidgetIds.
    val picasso = PicassoInitializer.get()
    picasso.load(Data.URLS[Random().nextInt(Data.URLS.size)])
      .placeholder(R.drawable.placeholder)
      .error(R.drawable.error)
      .transform(GrayscaleTransformation(picasso))
      .into(updateViews, R.id.image, appWidgetIds)
  }
}
