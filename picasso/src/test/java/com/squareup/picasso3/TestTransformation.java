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

class TestTransformation implements Transformation {
  private final String key;
  private final Bitmap result;

  TestTransformation(String key) {
    this(key, Bitmap.createBitmap(10, 10, null));
  }

  TestTransformation(String key, Bitmap result) {
    this.key = key;
    this.result = result;
  }

  @Override public RequestHandler.Result transform(RequestHandler.Result source) {
    Bitmap bitmap = source.getBitmap();
    if (bitmap == null) {
      return source;
    }

    bitmap.recycle();
    return new RequestHandler.Result(result, source.getLoadedFrom(), source.getExifRotation());
  }

  @Override public String key() {
    return key;
  }
}
