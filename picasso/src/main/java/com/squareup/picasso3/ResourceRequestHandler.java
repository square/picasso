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
import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static com.squareup.picasso3.BitmapUtils.decodeResource;
import static com.squareup.picasso3.BitmapUtils.isXmlResource;
import static com.squareup.picasso3.Picasso.LoadedFrom.DISK;

class ResourceRequestHandler extends RequestHandler {
  private final Context context;

  ResourceRequestHandler(Context context) {
    this.context = context;
  }

  @Override public boolean canHandleRequest(@NonNull Request data) {
    if (data.resourceId != 0 && !isXmlResource(context.getResources(), data.resourceId)) {
      return true;
    }
    return data.uri != null && SCHEME_ANDROID_RESOURCE.equals(data.uri.getScheme());
  }

  @Override
  public void load(@NonNull Picasso picasso, @NonNull Request request, @NonNull Callback callback) {
    boolean signaledCallback = false;
    try {
      Bitmap bitmap = decodeResource(context, request);
      signaledCallback = true;
      callback.onSuccess(new Result(bitmap, DISK));
    } catch (Exception e) {
      if (!signaledCallback) {
        callback.onError(e);
      }
    }
  }
}
