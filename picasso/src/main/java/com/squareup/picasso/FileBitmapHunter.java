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

import static android.media.ExifInterface.ORIENTATION_NORMAL;
import static android.media.ExifInterface.ORIENTATION_ROTATE_180;
import static android.media.ExifInterface.ORIENTATION_ROTATE_270;
import static android.media.ExifInterface.ORIENTATION_ROTATE_90;
import static android.media.ExifInterface.TAG_ORIENTATION;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.FROYO;
import static android.provider.MediaStore.Video.Thumbnails.FULL_SCREEN_KIND;
import static android.provider.MediaStore.Video.Thumbnails.MICRO_KIND;
import static android.provider.MediaStore.Video.Thumbnails.MINI_KIND;
import static com.squareup.picasso.FileBitmapHunter.PicassoKind.FULL;
import static com.squareup.picasso.FileBitmapHunter.PicassoKind.MICRO;
import static com.squareup.picasso.FileBitmapHunter.PicassoKind.MINI;

import java.io.IOException;
import java.net.URLConnection;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;

class FileBitmapHunter extends ContentStreamBitmapHunter {

  FileBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(context, picasso, dispatcher, cache, stats, action);
  }

  @Override Bitmap decode(Request data)
      throws IOException {
    setExifRotation(getFileExifRotation(data.uri));
    if (SDK_INT >= FROYO) {
        String mimeType = URLConnection.guessContentTypeFromName(data.uri.getPath());
        boolean isVideo = mimeType != null && mimeType.startsWith("video/");
        if (isVideo) {
            int kind;
            if(data.hasSize()) {
                PicassoKind picassoKind = getPicassoKind(data.targetWidth, data.targetHeight);
                kind = picassoKind.androidKind;
            } else {
                kind = FULL_SCREEN_KIND;
            }
            return createVideoThumbnail(data.uri, kind);
        }
    }

    return super.decode(data);
  }

  static int getFileExifRotation(Uri uri) throws IOException {
    ExifInterface exifInterface = new ExifInterface(uri.getPath());
    int orientation = exifInterface.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
    switch (orientation) {
      case ORIENTATION_ROTATE_90:
        return 90;
      case ORIENTATION_ROTATE_180:
        return 180;
      case ORIENTATION_ROTATE_270:
        return 270;
      default:
        return 0;
    }
  }

  static PicassoKind getPicassoKind(int targetWidth, int targetHeight) {
      if (targetWidth <= MICRO.width && targetHeight <= MICRO.height) {
          return MICRO;
      } else if (targetWidth <= MINI.width && targetHeight <= MINI.height) {
          return MINI;
      }
      return FULL;
  }

  // Only for SDK 8 and later, (FROYO, June 2010: Android 2.2)
  @TargetApi(Build.VERSION_CODES.FROYO)
  protected Bitmap createVideoThumbnail(Uri uri, int kind) {
    return ThumbnailUtils.createVideoThumbnail(uri.getPath(), kind);
  }

  enum PicassoKind {
      MICRO(MICRO_KIND, 96, 96),
      MINI(MINI_KIND, 512, 384),
      FULL(FULL_SCREEN_KIND, -1, -1);

      final int androidKind;
      final int width;
      final int height;

      PicassoKind(int androidKind, int width, int height) {
          this.androidKind = androidKind;
          this.width = width;
          this.height = height;
      }
  }
}
