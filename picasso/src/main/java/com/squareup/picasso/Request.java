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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.List;

class Request {

  enum LoadedFrom {
    MEMORY(Color.GREEN),
    DISK(Color.YELLOW),
    NETWORK(Color.RED);

    final int debugColor;

    private LoadedFrom(int debugColor) {
      this.debugColor = debugColor;
    }
  }

  static class RequestWeakReference<T> extends WeakReference<T> {
    final Request request;

    public RequestWeakReference(Request request, T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.request = request;
    }
  }

  final Picasso picasso;
  final Uri uri;
  final int resourceId;
  final WeakReference<ImageView> target;
  final PicassoBitmapOptions options;
  final List<Transformation> transformations;
  final boolean skipCache;
  final boolean noFade;
  final int errorResId;
  final Drawable errorDrawable;
  final String key;

  boolean cancelled;

  Request(Picasso picasso, Uri uri, int resourceId, ImageView imageView,
      PicassoBitmapOptions options, List<Transformation> transformations, boolean skipCache,
      boolean noFade, int errorResId, Drawable errorDrawable, String key) {
    this.picasso = picasso;
    this.uri = uri;
    this.resourceId = resourceId;
    this.target = new RequestWeakReference<ImageView>(this, imageView, picasso.referenceQueue);
    this.options = options;
    this.transformations = transformations;
    this.skipCache = skipCache;
    this.noFade = noFade;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.key = key;
  }

  Object getTarget() {
    return target.get();
  }

  void complete(Bitmap result, LoadedFrom from) {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", this));
    }

    ImageView target = this.target.get();
    if (target != null) {
      Context context = picasso.context;
      boolean debugging = picasso.debugging;
      PicassoDrawable.setBitmap(target, context, result, from, noFade, debugging);
    }
  }

  void error() {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    if (errorResId != 0) {
      target.setImageResource(errorResId);
    } else if (errorDrawable != null) {
      target.setImageDrawable(errorDrawable);
    }
  }

  Uri getUri() {
    return uri;
  }

  String getKey() {
    return key;
  }

  int getResourceId() {
    return resourceId;
  }

  boolean isCancelled() {
    return cancelled;
  }

  Picasso getPicasso() {
    return picasso;
  }
}
