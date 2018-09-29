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
package com.squareup.picasso3;

import android.app.Notification;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.widget.RemoteViews;

abstract class RemoteViewsAction extends Action {
  @Nullable Callback callback;
  final @DrawableRes int errorResId;
  final RemoteViewsTarget target;

  RemoteViewsAction(Picasso picasso, Request data, @DrawableRes int errorResId,
      @NonNull RemoteViewsTarget target, @Nullable Callback callback) {
    super(picasso, data);
    this.errorResId = errorResId;
    this.target = target;
    this.callback = callback;
  }

  @Override void complete(RequestHandler.Result result) {
    target.remoteViews.setImageViewBitmap(target.viewId, result.getBitmap());
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

  void setImageResource(int resId) {
    target.remoteViews.setImageViewResource(target.viewId, resId);
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

    AppWidgetAction(Picasso picasso, Request data, @DrawableRes int errorResId,
        RemoteViewsTarget target, int[] appWidgetIds, @Nullable Callback callback) {
      super(picasso, data, errorResId, target, callback);
      this.appWidgetIds = appWidgetIds;
    }

    @Override void update() {
      AppWidgetManager manager = AppWidgetManager.getInstance(picasso.context);
      manager.updateAppWidget(appWidgetIds, target.remoteViews);
    }

    @Override Object getTarget() {
      return target;
    }
  }

  static class NotificationAction extends RemoteViewsAction {
    private final int notificationId;
    @Nullable private final String notificationTag;
    private final Notification notification;

    NotificationAction(Picasso picasso, Request data, @DrawableRes int errorResId,
        RemoteViewsTarget target, int notificationId, Notification notification,
        @Nullable String notificationTag, @Nullable Callback callback) {
      super(picasso, data, errorResId, target, callback);
      this.notificationId = notificationId;
      this.notificationTag = notificationTag;
      this.notification = notification;
    }

    @Override void update() {
      NotificationManager manager =
          ContextCompat.getSystemService(picasso.context, NotificationManager.class);
      if (manager != null) {
        manager.notify(notificationTag, notificationId, notification);
      }
    }

    @Override Object getTarget() {
      return target;
    }
  }
}
