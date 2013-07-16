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
import android.net.Uri;
import java.lang.ref.WeakReference;
import java.util.List;

final class TargetRequest extends Request {

  private final WeakReference<Target> weakTarget;

  TargetRequest(Picasso picasso, Uri uri, int resourceId, Target target,
      PicassoBitmapOptions bitmapOptions, List<Transformation> transformations, boolean skipCache,
      String key) {
    super(picasso, uri, resourceId, null, bitmapOptions, transformations, skipCache, false, 0, null,
        key);
    this.weakTarget = new WeakReference<Target>(target, picasso.referenceQueue);
  }

  @Override Target getTarget() {
    return weakTarget.get();
  }

  @Override void complete(Bitmap result, LoadedFrom from) {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", this));
    }
    Target target = getTarget();
    if (target != null) {
      target.onSuccess(result);
      if (result.isRecycled()) {
        throw new IllegalStateException("Target callback must not recycle bitmap!");
      }
    }
  }

  @Override void error() {
    Target target = getTarget();
    if (target == null) {
      return;
    }
    target.onError();
  }
}
