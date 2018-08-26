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
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;
import java.io.FileNotFoundException;
import java.io.IOException;
import okio.Source;

import static android.content.ContentResolver.SCHEME_FILE;
import static android.support.media.ExifInterface.ORIENTATION_NORMAL;
import static android.support.media.ExifInterface.TAG_ORIENTATION;
import static com.squareup.picasso3.BitmapUtils.decodeStream;
import static com.squareup.picasso3.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso3.Utils.checkNotNull;

class FileRequestHandler extends ContentStreamRequestHandler {

  FileRequestHandler(Context context) {
    super(context);
  }

  @Override public boolean canHandleRequest(@NonNull Request data) {
    Uri uri = data.uri;
    return uri != null && SCHEME_FILE.equals(uri.getScheme());
  }

  @Override
  public void load(@NonNull Picasso picasso, @NonNull Request request, @NonNull Callback callback) {
    boolean signaledCallback = false;
    try {
      Uri requestUri = checkNotNull(request.uri, "request.uri == null");
      Source source = getSource(requestUri);
      Bitmap bitmap = decodeStream(source, request);
      int exifRotation = getExifOrientation(requestUri);
      signaledCallback = true;
      callback.onSuccess(new Result(bitmap, DISK, exifRotation));
    } catch (Exception e) {
      if (!signaledCallback) {
        callback.onError(e);
      }
    }
  }

  @Override protected int getExifOrientation(Uri uri) throws IOException {
    String path = uri.getPath();
    if (path == null) {
      throw new FileNotFoundException("path == null, uri: " + uri);
    }
    ExifInterface exifInterface = new ExifInterface(path);
    return exifInterface.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
  }
}
