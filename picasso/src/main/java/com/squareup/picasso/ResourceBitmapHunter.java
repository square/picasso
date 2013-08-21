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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class ResourceBitmapHunter extends BitmapHunter {
  private final Context context;

  ResourceBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(picasso, dispatcher, cache, stats, action);
    this.context = context;
  }

  @Override Bitmap decode(Request data) throws IOException {
    return decodeResource(context.getResources(), data);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  private Bitmap decodeResource(Resources resources, Request data) {
    int resourceId = data.resourceId;
    BitmapFactory.Options bitmapOptions = null;
    if (data.hasSize()) {
      bitmapOptions = new BitmapFactory.Options();
      bitmapOptions.inJustDecodeBounds = true;
      BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
      calculateInSampleSize(data.targetWidth, data.targetHeight, bitmapOptions);
    }
    return BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
  }
}
