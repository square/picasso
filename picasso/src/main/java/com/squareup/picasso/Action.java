/*
 * Copyright (C) 2013 Square, Inc.
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

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import com.squareup.picasso.Picasso.Priority;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

abstract class Action<T> {
  static class RequestWeakReference<T> extends WeakReference<T> {
    final Action action;

    public RequestWeakReference(Action action, T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.action = action;
    }
  }

  final Picasso picasso;
  final Request request;
  final WeakReference<T> target;
  final boolean skipCache;
  final boolean noFade;
  final int errorResId;
  final Drawable errorDrawable;
  final String key;
  final Object tag;

  boolean willReplay;
  boolean cancelled;

  Action(Picasso picasso, T target, Request request, boolean skipCache, boolean noFade,
      int errorResId, Drawable errorDrawable, String key, Object tag) {
    this.picasso = picasso;
    this.request = request;
    this.target =
        target == null ? null : new RequestWeakReference<T>(this, target, picasso.referenceQueue);
    this.skipCache = skipCache;
    this.noFade = noFade;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.key = key;
    this.tag = (tag != null ? tag : this);
  }

  abstract void complete(Bitmap result, Picasso.LoadedFrom from);

  abstract void error();

  void cancel() {
    cancelled = true;
  }

  Request getRequest() {
    return request;
  }

  T getTarget() {
    return target == null ? null : target.get();
  }

  String getKey() {
    return key;
  }

  boolean isCancelled() {
    return cancelled;
  }

  boolean willReplay() {
    return willReplay;
  }

  Picasso getPicasso() {
    return picasso;
  }

  Priority getPriority() {
    return request.priority;
  }

  Object getTag() {
    return tag;
  }
}
