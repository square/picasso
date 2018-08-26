/*
 * Copyright (C) 2018 Square, Inc.
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
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

import static com.squareup.picasso3.BitmapUtils.isXmlResource;
import static com.squareup.picasso3.Picasso.LoadedFrom.DISK;

public final class ResourceDrawableRequestHandler extends RequestHandler {
  private final Context context;
  private final DrawableLoader loader;

  private ResourceDrawableRequestHandler(Context context, DrawableLoader loader) {
    this.context = context;
    this.loader = loader;
  }

  @Override public boolean canHandleRequest(@NonNull Request data) {
    return data.resourceId != 0 && isXmlResource(context.getResources(), data.resourceId);
  }

  @Override
  public void load(@NonNull Picasso picasso, @NonNull Request request, @NonNull Callback callback) {
    Drawable drawable = loader.load(request.resourceId);
    if (drawable == null) {
      callback.onError(new IllegalArgumentException(
          "invalid resId: " + Integer.toHexString(request.resourceId)));
    } else {
      callback.onSuccess(new Result(drawable, DISK));
    }
  }

  @NonNull
  public static ResourceDrawableRequestHandler create(@NonNull Context context,
      @NonNull DrawableLoader loader) {
    return new ResourceDrawableRequestHandler(context, loader);
  }

  @NonNull
  public static ResourceDrawableRequestHandler create(@NonNull final Context context) {
    return create(context, new DrawableLoader() {
      @Override public Drawable load(int resId) {
        return ContextCompat.getDrawable(context, resId);
      }
    });
  }
}
