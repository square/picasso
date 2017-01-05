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

abstract class RemoteViewsAction extends Action<RemoteViewsAction.RemoteViewsTarget> {
  final RemoteViews remoteViews;
  final int viewId;
  Callback callback;

  private RemoteViewsTarget target;

  RemoteViewsAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
      int errorResId, int memoryPolicy, int networkPolicy, Object tag, String key,
      Callback callback) {
    super(picasso, null, data, memoryPolicy, networkPolicy, errorResId, null, key, tag, false);
    this.remoteViews = remoteViews;
    this.viewId = viewId;
    this.callback = callback;
  }

  @Override void complete(Bitmap result, Picasso.LoadedFrom from) {
    remoteViews.setImageViewBitmap(viewId, result);
    update();
    if (callback != null) {
      callback.onSuccess();
    }
  }

  @Override void cancel() {
    super.cancel();
    if (callback != null) {
      callback = null;
    }
  }

  @Override public void error(Exception e) {
    if (errorResId != 0) {
      setImageResource(errorResId);
    }
    if (callback != null) {
      callback.onError(e);
    }
  }

  @Override RemoteViewsTarget getTarget() {
    if (target == null) {
      target = new RemoteViewsTarget(remoteViews, viewId);
    }
    return target;
  }

  void setImageResource(int resId) {
    remoteViews.setImageViewResource(viewId, resId);
    update();
  }

  abstract void update();

  static class RemoteViewsTarget {
    final RemoteViews remoteViews;
    final int viewId;

    RemoteViewsTarget(RemoteViews remoteViews, int viewId) {
      this.remoteViews = remoteViews;
      this.viewId = viewId;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RemoteViewsTarget remoteViewsTarget = (RemoteViewsTarget) o;
      return viewId == remoteViewsTarget.viewId && remoteViews.equals(
          remoteViewsTarget.remoteViews);
    }

    @Override public int hashCode() {
      return 31 * remoteViews.hashCode() + viewId;
    }
  }

  static class AppWidgetAction extends RemoteViewsAction {
    private final int[] appWidgetIds;

    AppWidgetAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
        int[] appWidgetIds, int memoryPolicy, int networkPolicy, String key, Object tag,
        int errorResId, Callback callback) {
      super(picasso, data, remoteViews, viewId, errorResId, memoryPolicy, networkPolicy, tag, key,
          callback);
      this.appWidgetIds = appWidgetIds;
    }

    @Override void update() {
      AppWidgetManager manager = AppWidgetManager.getInstance(picasso.context);
      manager.updateAppWidget(appWidgetIds, remoteViews);
    }
  }

  static class NotificationAction extends RemoteViewsAction {
    private final int notificationId;
    private final String notificationTag;
    private final Notification notification;

    NotificationAction(Picasso picasso, Request data, RemoteViews remoteViews, int viewId,
        int notificationId, Notification notification, String notificationTag, int memoryPolicy,
        int networkPolicy, String key, Object tag, int errorResId, Callback callback) {
      super(picasso, data, remoteViews, viewId, errorResId, memoryPolicy, networkPolicy, tag, key,
          callback);
      this.notificationId = notificationId;
      this.notificationTag = notificationTag;
      this.notification = notification;
    }

    @Override void update() {
      NotificationManager manager = getService(picasso.context, NOTIFICATION_SERVICE);
      manager.notify(notificationTag, notificationId, notification);
    }
  }
}
