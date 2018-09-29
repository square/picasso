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

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.widget.ImageView;

class ImageViewAction extends Action {
  @Nullable Callback callback;
  final ImageView target;
  @Nullable final Drawable errorDrawable;
  final @DrawableRes int errorResId;
  final boolean noFade;

  ImageViewAction(Picasso picasso, ImageView target, Request data, @Nullable Drawable errorDrawable,
      @DrawableRes int errorResId, boolean noFade, @Nullable Callback callback) {
    super(picasso, data);
    this.target = target;
    this.errorDrawable = errorDrawable;
    this.errorResId = errorResId;
    this.noFade = noFade;
    this.callback = callback;
  }

  @Override public void complete(RequestHandler.Result result) {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete action with no result!\n%s", this));
    }

    boolean indicatorsEnabled = picasso.indicatorsEnabled;
    PicassoDrawable.setResult(target, picasso.context, result, noFade, indicatorsEnabled);

    if (callback != null) {
      callback.onSuccess();
    }
  }

  @Override public void error(Exception e) {
    Drawable placeholder = target.getDrawable();
    if (placeholder instanceof Animatable) {
      ((Animatable) placeholder).stop();
    }
    if (errorResId != 0) {
      target.setImageResource(errorResId);
    } else if (errorDrawable != null) {
      target.setImageDrawable(errorDrawable);
    }

    if (callback != null) {
      callback.onError(e);
    }
  }

  @Override Object getTarget() {
    return target;
  }

  @Override void cancel() {
    super.cancel();
    if (callback != null) {
      callback = null;
    }
  }
}
