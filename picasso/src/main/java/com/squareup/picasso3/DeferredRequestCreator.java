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
package com.squareup.picasso3;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.ImageView;

class DeferredRequestCreator implements OnPreDrawListener, OnAttachStateChangeListener {
  private final RequestCreator creator;
  @VisibleForTesting final ImageView target;
  @VisibleForTesting @Nullable Callback callback;

  DeferredRequestCreator(RequestCreator creator, ImageView target, @Nullable Callback callback) {
    this.creator = creator;
    this.target = target;
    this.callback = callback;

    target.addOnAttachStateChangeListener(this);

    // Only add the pre-draw listener if the view is already attached.
    // See: https://github.com/square/picasso/issues/1321
    if (target.getWindowToken() != null) {
      onViewAttachedToWindow(target);
    }
  }

  @Override public void onViewAttachedToWindow(View view) {
    view.getViewTreeObserver().addOnPreDrawListener(this);
  }

  @Override public void onViewDetachedFromWindow(View view) {
    view.getViewTreeObserver().removeOnPreDrawListener(this);
  }

  @Override public boolean onPreDraw() {
    ImageView target = this.target;

    ViewTreeObserver vto = target.getViewTreeObserver();
    if (!vto.isAlive()) {
      return true;
    }

    int width = target.getWidth();
    int height = target.getHeight();

    if (width <= 0 || height <= 0) {
      return true;
    }

    target.removeOnAttachStateChangeListener(this);
    vto.removeOnPreDrawListener(this);

    this.creator.unfit().resize(width, height).into(target, callback);
    return true;
  }

  void cancel() {
    creator.clearTag();
    callback = null;

    target.removeOnAttachStateChangeListener(this);

    ViewTreeObserver vto = target.getViewTreeObserver();
    if (vto.isAlive()) {
      vto.removeOnPreDrawListener(this);
    }
  }

  @Nullable Object getTag() {
    return creator.getTag();
  }
}
