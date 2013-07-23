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
import android.net.Uri;
import java.io.IOException;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class ResourceBitmapHunter extends BitmapHunter {
  private final int resourceId;
  private final Context context;

  ResourceBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Request request) {
    super(picasso, dispatcher, cache, request);
    this.context = context;
    this.resourceId = request.resourceId;
  }

  @Override Bitmap decode(Uri uri, PicassoBitmapOptions options, int retryCount)
      throws IOException {
    return decodeResource(context.getResources(), resourceId, options);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  @Override String getName() {
    return Integer.toString(resourceId);
  }

  private Bitmap decodeResource(Resources resources, int resourceId,
      PicassoBitmapOptions bitmapOptions) {
    if (bitmapOptions != null && bitmapOptions.inJustDecodeBounds) {
      BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
      calculateInSampleSize(bitmapOptions);
    }
    return BitmapFactory.decodeResource(resources, resourceId, bitmapOptions);
  }
}
