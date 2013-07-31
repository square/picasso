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
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class ContentStreamBitmapHunter extends BitmapHunter {

  final Context context;

  ContentStreamBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Request request) {
    super(picasso, dispatcher, cache, stats, request);
    this.context = context;
  }

  @Override Bitmap decode(Uri uri, PicassoBitmapOptions options, int retryCount)
      throws IOException {
    return decodeContentStream(uri, options);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  private Bitmap decodeContentStream(Uri path, PicassoBitmapOptions options) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    if (options != null && options.inJustDecodeBounds) {
      InputStream is = null;
      try {
        is = contentResolver.openInputStream(path);
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(options);
    }
    InputStream is = contentResolver.openInputStream(path);
    try {
      return BitmapFactory.decodeStream(is, null, options);
    } finally {
      Utils.closeQuietly(is);
    }
  }
}
