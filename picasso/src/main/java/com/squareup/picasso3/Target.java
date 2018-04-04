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

import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

public class Target<T> {
  T target;
  final Drawable errorDrawable;
  final int errorResId;
  final boolean noFade;

  Target(@NonNull T target) {
    this.target = target;
    this.errorResId = 0;
    this.errorDrawable = null;
    this.noFade = false;
  }

  Target(@NonNull T target, @DrawableRes int errorResId) {
    this.target = target;
    this.errorResId = errorResId;
    this.errorDrawable = null;
    this.noFade = false;
  }

  Target(@NonNull T target, Drawable errorDrawable) {
    this.target = target;
    this.errorResId = 0;
    this.errorDrawable = errorDrawable;
    this.noFade = false;
  }

  Target(@NonNull T target, @DrawableRes int errorResId, Drawable errorDrawable, boolean noFade) {
    this.target = target;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.noFade = noFade;
  }
}
