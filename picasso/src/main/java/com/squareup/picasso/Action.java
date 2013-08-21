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
  final Request data;
  final WeakReference<T> target;
  final boolean skipCache;
  final boolean noFade;
  final int errorResId;
  final Drawable errorDrawable;
  final String key;

  boolean cancelled;

  Action(Picasso picasso, T target, Request data, boolean skipCache, boolean noFade,
      int errorResId, Drawable errorDrawable, String key) {
    this.picasso = picasso;
    this.data = data;
    this.target = new RequestWeakReference<T>(this, target, picasso.referenceQueue);
    this.skipCache = skipCache;
    this.noFade = noFade;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.key = key;
  }

  abstract void complete(Bitmap result, Picasso.LoadedFrom from);

  abstract void error();

  void cancel() {
    cancelled = true;
  }

  Request getData() {
    return data;
  }

  T getTarget() {
    return target.get();
  }

  String getKey() {
    return key;
  }

  boolean isCancelled() {
    return cancelled;
  }

  Picasso getPicasso() {
    return picasso;
  }
}
