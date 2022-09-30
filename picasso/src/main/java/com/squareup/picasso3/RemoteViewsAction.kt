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
package com.squareup.picasso3

import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget
import com.squareup.picasso3.RequestHandler.Result
import com.squareup.picasso3.RequestHandler.Result.Bitmap

internal abstract class RemoteViewsAction(
  picasso: Picasso,
  data: Request,
  target: RemoteViewsTarget,
  @DrawableRes val errorResId: Int,
  var callback: Callback?
) : Action<RemoteViewsTarget>(picasso, data, target) {
  override fun complete(result: Result) {
    if (result is Bitmap) {
      target?.let { it.remoteViews.setImageViewBitmap(it.viewId, result.bitmap) }
      update()
      callback?.onSuccess()
    }
  }

  override fun cancel() {
    super.cancel()
    callback = null
  }

  override fun error(e: Exception) {
    if (errorResId != 0) {
      setImageResource(errorResId)
    }
    callback?.onError(e)
  }

  fun setImageResource(resId: Int) {
    target?.let { it.remoteViews.setImageViewResource(it.viewId, resId) }
    update()
  }

  abstract fun update()

  internal class RemoteViewsTarget(
    val remoteViews: RemoteViews,
    val viewId: Int
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val remoteViewsTarget = other as RemoteViewsTarget
      return viewId == remoteViewsTarget.viewId && remoteViews ==
        remoteViewsTarget.remoteViews
    }

    override fun hashCode(): Int {
      return 31 * remoteViews.hashCode() + viewId
    }
  }

  internal class AppWidgetAction(
    picasso: Picasso,
    data: Request,
    target: RemoteViewsTarget,
    @DrawableRes errorResId: Int,
    private val appWidgetIds: IntArray,
    callback: Callback?
  ) : RemoteViewsAction(picasso, data, target, errorResId, callback) {
    override fun update() {
      val manager = AppWidgetManager.getInstance(picasso.context)
      target?.let { manager.updateAppWidget(appWidgetIds, it.remoteViews) }
    }
  }

  internal class NotificationAction(
    picasso: Picasso,
    data: Request,
    @DrawableRes errorResId: Int,
    target: RemoteViewsTarget,
    private val notificationId: Int,
    private val notification: Notification,
    private val notificationTag: String?,
    callback: Callback?
  ) : RemoteViewsAction(picasso, data, target, errorResId, callback) {
    override fun update() {
      val manager = ContextCompat.getSystemService(
        picasso.context, NotificationManager::class.java
      )
      manager?.notify(notificationTag, notificationId, notification)
    }
  }
}
