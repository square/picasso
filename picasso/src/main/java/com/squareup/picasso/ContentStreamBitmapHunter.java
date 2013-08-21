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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class ContentStreamBitmapHunter extends BitmapHunter {
  final Context context;

  ContentStreamBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(picasso, dispatcher, cache, stats, action);
    this.context = context;
  }

  @Override Bitmap decode(Request data)
      throws IOException {
    return decodeContentStream(data);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  protected Bitmap decodeContentStream(Request data) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    BitmapFactory.Options options = null;
    if (data.hasSize()) {
      options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      InputStream is = null;
      try {
        is = contentResolver.openInputStream(data.uri);
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(data.targetWidth, data.targetHeight, options);
    }
    InputStream is = contentResolver.openInputStream(data.uri);
    try {
      return BitmapFactory.decodeStream(is, null, options);
    } finally {
      Utils.closeQuietly(is);
    }
  }
}
