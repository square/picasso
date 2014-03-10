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
package com.squareup.picasso;

import android.app.Notification;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.squareup.picasso.Utils.getService;

abstract class RemoteViewsAction extends Action<Void> {
  final RemoteViews remoteViews;
  final int viewId;

  RemoteViewsAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
      int errorResId, boolean skipCache, String key) {
    super(picasso, null, data, skipCache, false, errorResId, null, key);
    this.remoteViews = remoteViews;
    this.viewId = viewId;
  }

  @Override void complete(Bitmap result, Picasso.LoadedFrom from) {
    remoteViews.setImageViewBitmap(viewId, result);
    update();
  }

  @Override public void error() {
    if (errorResId != 0) {
      setImageResource(errorResId);
    }
  }

  void setImageResource(int resId) {
    remoteViews.setImageViewResource(viewId, resId);
    update();
  }

  abstract void update();

  static class AppWidgetAction extends RemoteViewsAction {
    private final int[] appWidgetIds;

    AppWidgetAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
        int[] appWidgetIds, boolean skipCache, int errorResId, String key) {
      super(picasso, data, remoteViews, viewId, errorResId, skipCache, key);
      this.appWidgetIds = appWidgetIds;
    }

    @Override void update() {
      AppWidgetManager manager = AppWidgetManager.getInstance(picasso.context);
      manager.updateAppWidget(appWidgetIds, remoteViews);
    }
  }

  static class NotificationAction extends RemoteViewsAction {
    private final int notificationId;
    private final Notification notification;

    NotificationAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
        int notificationId, Notification notification, boolean skipCache, int errorResId,
        String key) {
      super(picasso, data, remoteViews, viewId, errorResId, skipCache, key);
      this.notificationId = notificationId;
      this.notification = notification;
    }

    @Override void update() {
      NotificationManager manager = getService(picasso.context, NOTIFICATION_SERVICE);
      manager.notify(notificationId, notification);
    }
  }
}
