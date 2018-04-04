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
import android.support.v4.content.ContextCompat;
import android.widget.RemoteViews;

abstract class RemoteViewsAction extends Action<RemoteViewsAction.RemoteViewsTarget> {
  Target<RemoteViewsTarget> remoteWrapper;
  Callback callback;


  RemoteViewsAction(Picasso picasso, Request data, Target<RemoteViewsTarget> wrapper,
      Callback callback) {
    super(picasso, null, data);
    this.remoteWrapper = wrapper;
    this.callback = callback;
  }

  @Override void complete(RequestHandler.Result result) {
    RemoteViewsTarget target = remoteWrapper.target;
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
    if (remoteWrapper.errorResId != 0) {
      setImageResource(remoteWrapper.errorResId);
    }
    if (callback != null) {
      callback.onError(e);
    }
  }

  @Override RemoteViewsTarget getTarget() {
    return remoteWrapper.target;
  }

  void setImageResource(int resId) {
    RemoteViewsTarget target = remoteWrapper.target;
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

    AppWidgetAction(Picasso picasso, Request data, Target<RemoteViewsTarget> wrapper,
        int[] appWidgetIds, Callback callback) {
      super(picasso, data, wrapper, callback);
      this.appWidgetIds = appWidgetIds;
    }

    @Override void update() {
      AppWidgetManager manager = AppWidgetManager.getInstance(picasso.context);
      manager.updateAppWidget(appWidgetIds, remoteWrapper.target.remoteViews);
    }
  }

  static class NotificationAction extends RemoteViewsAction {
    private final int notificationId;
    private final String notificationTag;
    private final Notification notification;

    NotificationAction(Picasso picasso, Request data, Target<RemoteViewsTarget> wrapper,
        int notificationId, Notification notification, String notificationTag, Callback callback) {
      super(picasso, data, wrapper, callback);
      this.notificationId = notificationId;
      this.notificationTag = notificationTag;
      this.notification = notification;
    }

    @Override void update() {
      NotificationManager manager =
          ContextCompat.getSystemService(picasso.context, NotificationManager.class);
      manager.notify(notificationTag, notificationId, notification);
    }
  }
}
