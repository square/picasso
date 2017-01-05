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

import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

class DeferredRequestCreator implements ViewTreeObserver.OnPreDrawListener {

  final RequestCreator creator;
  final WeakReference<ImageView> target;
  OnAttachStateChangeListener attachListener;
  Callback callback;

  @VisibleForTesting DeferredRequestCreator(RequestCreator creator, ImageView target) {
    this(creator, target, null);
  }

  DeferredRequestCreator(RequestCreator creator, ImageView target, Callback callback) {
    this.creator = creator;
    this.target = new WeakReference<>(target);
    this.callback = callback;

    // Since the view on which an image is being requested might not be attached to a hierarchy,
    // defer adding the pre-draw listener until the view is attached. This works around a platform
    // behavior where a global, dummy VTO is used until a real one is available on attach.
    // See: https://github.com/square/picasso/issues/1321
    if (target.getWindowToken() == null) {
      defer(target);
    } else {
      target.getViewTreeObserver().addOnPreDrawListener(this);
    }
  }

  @Override public boolean onPreDraw() {
    ImageView target = this.target.get();
    if (target == null) {
      return true;
    }
    ViewTreeObserver vto = target.getViewTreeObserver();
    if (!vto.isAlive()) {
      return true;
    }

    int width = target.getWidth();
    int height = target.getHeight();

    if (width <= 0 || height <= 0 || target.isLayoutRequested()) {
      return true;
    }

    vto.removeOnPreDrawListener(this);
    this.target.clear();

    this.creator.unfit().resize(width, height).into(target, callback);
    return true;
  }

  void cancel() {
    creator.clearTag();
    callback = null;

    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    this.target.clear();

    if (attachListener != null) {
      target.removeOnAttachStateChangeListener(attachListener);
      attachListener = null;
    } else {
      ViewTreeObserver vto = target.getViewTreeObserver();
      if (!vto.isAlive()) {
        return;
      }
      vto.removeOnPreDrawListener(this);
    }
  }

  Object getTag() {
    return creator.getTag();
  }

  private void defer(View view) {
    attachListener = new OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View view) {
        view.removeOnAttachStateChangeListener(this);
        view.getViewTreeObserver().addOnPreDrawListener(DeferredRequestCreator.this);

        attachListener = null;
      }

      @Override public void onViewDetachedFromWindow(View view) {
      }
    };
    view.addOnAttachStateChangeListener(attachListener);
  }
}
