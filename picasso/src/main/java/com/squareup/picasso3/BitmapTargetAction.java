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

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;

final class BitmapTargetAction extends Action {
  final @Nullable Drawable errorDrawable;
  final @DrawableRes int errorResId;
  final BitmapTarget target;

  BitmapTargetAction(Picasso picasso, BitmapTarget target, Request data,
      @Nullable Drawable errorDrawable, @DrawableRes int errorResId) {
    super(picasso, data);
    this.target = target;
    this.errorDrawable = errorDrawable;
    this.errorResId = errorResId;
  }

  @Override void complete(RequestHandler.Result result) {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete action with no result!\n%s", this));
    }
    Bitmap bitmap = result.getBitmap();
    if (bitmap != null) {
      target.onBitmapLoaded(bitmap, result.getLoadedFrom());
      if (bitmap.isRecycled()) {
        throw new IllegalStateException("Target callback must not recycle bitmap!");
      }
    }
  }

  @Override void error(Exception e) {
    if (errorResId != 0) {
      target.onBitmapFailed(e,
          ContextCompat.getDrawable(picasso.context, errorResId));
    } else {
      target.onBitmapFailed(e, errorDrawable);
    }
  }

  @Override Object getTarget() {
    return target;
  }
}
