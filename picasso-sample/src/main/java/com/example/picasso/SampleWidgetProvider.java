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
package com.example.picasso;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.provider.PicassoProvider;
import java.util.Random;

public class SampleWidgetProvider extends AppWidgetProvider {

  @Override
  public void onUpdate(final Context context, AppWidgetManager appWidgetManager,
      int[] appWidgetIds) {
    RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.sample_widget);
    // Load image for all appWidgetIds.
    Picasso picasso = PicassoProvider.get();
    picasso.load(Data.URLS[new Random().nextInt(Data.URLS.length)]) //
        .placeholder(R.drawable.placeholder) //
        .error(R.drawable.error) //
        .transform(new GrayscaleTransformation(picasso)) //
        .into(updateViews, R.id.image, appWidgetIds);
  }
}
