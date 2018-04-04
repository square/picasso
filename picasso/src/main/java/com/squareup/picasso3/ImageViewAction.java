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

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

class ImageViewAction extends Action<ImageView> {

  Callback callback;

  ImageViewAction(Picasso picasso, Target<ImageView> target, Request data, Callback callback) {
    super(picasso, target, data);
    this.callback = callback;
  }

  @Override public void complete(RequestHandler.Result result) {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete action with no result!\n%s", this));
    }

    ImageView target = this.target.get();
    if (target == null) {
      return;
    }

    Context context = picasso.context;
    boolean indicatorsEnabled = picasso.indicatorsEnabled;
    PicassoDrawable.setResult(target, context, result, wrapper.noFade, indicatorsEnabled);

    if (callback != null) {
      callback.onSuccess();
    }
  }

  @Override public void error(Exception e) {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    Drawable placeholder = target.getDrawable();
    if (placeholder instanceof Animatable) {
      ((Animatable) placeholder).stop();
    }
    if (wrapper.errorResId != 0) {
      target.setImageResource(wrapper.errorResId);
    } else if (wrapper.errorDrawable != null) {
      target.setImageDrawable(wrapper.errorDrawable);
    }

    if (callback != null) {
      callback.onError(e);
    }
  }

  @Override void cancel() {
    super.cancel();
    if (callback != null) {
      callback = null;
    }
  }
}
