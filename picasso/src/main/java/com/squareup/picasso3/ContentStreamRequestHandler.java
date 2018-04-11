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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import okio.Okio;
import okio.Source;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.support.media.ExifInterface.ORIENTATION_NORMAL;
import static android.support.media.ExifInterface.TAG_ORIENTATION;
import static com.squareup.picasso3.Picasso.LoadedFrom.DISK;

class ContentStreamRequestHandler extends RequestHandler {
  final Context context;

  ContentStreamRequestHandler(Context context) {
    this.context = context;
  }

  @Override public boolean canHandleRequest(@NonNull Request data) {
    return SCHEME_CONTENT.equals(data.uri.getScheme());
  }

  @Override
  public void load(@NonNull Picasso picasso, @NonNull Request request, @NonNull Callback callback) {
    boolean signaledCallback = false;
    try {
      Source source = getSource(request);
      if (source == null) {
        callback.onError(new IllegalStateException("null source from " + request.uri));
        return;
      }
      Bitmap bitmap = decodeStream(source, request);
      int exifRotation = getExifOrientation(request);
      signaledCallback = true;
      callback.onSuccess(new Result(bitmap, DISK, exifRotation));
    } catch (Exception e) {
      if (!signaledCallback) {
        callback.onError(e);
      }
    }
  }

  Source getSource(Request request) throws FileNotFoundException {
    ContentResolver contentResolver = context.getContentResolver();
    InputStream inputStream = contentResolver.openInputStream(request.uri);
    return inputStream == null ? null : Okio.source(inputStream);
  }

  protected int getExifOrientation(Request request) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    InputStream inputStream = null;
    try {
      inputStream = contentResolver.openInputStream(request.uri);
      ExifInterface exifInterface = new ExifInterface(inputStream);
      return exifInterface.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
    } finally {
      try {
        if (inputStream != null) {
          inputStream.close();
        }
      } catch (IOException ignored) {
      }
    }
  }
}
