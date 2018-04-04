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
import android.support.v4.content.ContextCompat;

final class BitmapTargetAction extends Action<BitmapTarget> {

  BitmapTargetAction(Picasso picasso, Target<BitmapTarget> wrapper, Request data) {
    super(picasso, wrapper, data);
  }

  @Override void complete(RequestHandler.Result result) {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete action with no result!\n%s", this));
    }
    BitmapTarget target = getTarget();
    if (target != null) {
      Bitmap bitmap = result.getBitmap();
      if (bitmap != null) {
        target.onBitmapLoaded(bitmap, result.getLoadedFrom());
        if (bitmap.isRecycled()) {
          throw new IllegalStateException("Target callback must not recycle bitmap!");
        }
      }
    }
  }

  @Override void error(Exception e) {
    BitmapTarget target = getTarget();
    if (target != null) {
      if (wrapper.errorResId != 0) {
        target.onBitmapFailed(e,
            ContextCompat.getDrawable(picasso.context, wrapper.errorResId));
      } else {
        target.onBitmapFailed(e, wrapper.errorDrawable);
      }
    }
  }
}
