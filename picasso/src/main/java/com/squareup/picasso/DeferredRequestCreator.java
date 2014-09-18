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

import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.TestOnly;

import static com.squareup.picasso.Utils.getCallback;

class DeferredRequestCreator implements ViewTreeObserver.OnPreDrawListener {

  final RequestCreator creator;
  final WeakReference<ImageView> target;
  Callback callback;
  WeakReference<Callback> callbackRef;

  @TestOnly DeferredRequestCreator(RequestCreator creator, ImageView target) {
    this(creator, target, (Callback) null);
  }

  /**
   * Creates a {@link DeferredRequestCreator} which holds a strong reference to the given callback.
   */
  DeferredRequestCreator(RequestCreator creator, ImageView target, Callback callback) {
    this.creator = creator;
    this.target = new WeakReference<ImageView>(target);
    this.callback = callback;
    target.getViewTreeObserver().addOnPreDrawListener(this);
  }

  /**
   * Creates a {@link DeferredRequestCreator} which holds a weak reference to the given callback.
   */
   DeferredRequestCreator(RequestCreator creator, ImageView target,
       WeakReference<Callback> callbackRef) {
     this.creator = creator;
     this.target = new WeakReference<ImageView>(target);
     this.callbackRef = callbackRef;
     target.getViewTreeObserver().addOnPreDrawListener(this);
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

    if (width <= 0 || height <= 0) {
      return true;
    }

    vto.removeOnPreDrawListener(this);

    this.creator.unfit().resize(width, height).into(target, getCallback(callback, callbackRef));
    return true;
  }

  void cancel() {
    callback = null;
    if (callbackRef != null) {
      callbackRef.clear();
    }
    callbackRef = null;
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    ViewTreeObserver vto = target.getViewTreeObserver();
    if (!vto.isAlive()) {
      return;
    }
    vto.removeOnPreDrawListener(this);
  }

}
