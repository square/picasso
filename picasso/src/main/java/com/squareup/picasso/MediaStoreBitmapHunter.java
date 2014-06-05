/*
 * Copyright (C) 2014 Square, Inc.
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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.IOException;

import static android.content.ContentUris.parseId;
import static android.provider.MediaStore.Images;
import static android.provider.MediaStore.Video;
import static android.provider.MediaStore.Images.Thumbnails.FULL_SCREEN_KIND;
import static android.provider.MediaStore.Images.Thumbnails.MICRO_KIND;
import static android.provider.MediaStore.Images.Thumbnails.MINI_KIND;
import static com.squareup.picasso.MediaStoreBitmapHunter.PicassoKind.FULL;
import static com.squareup.picasso.MediaStoreBitmapHunter.PicassoKind.MICRO;
import static com.squareup.picasso.MediaStoreBitmapHunter.PicassoKind.MINI;

class MediaStoreBitmapHunter extends ContentStreamBitmapHunter {
  private static final String[] CONTENT_ORIENTATION = new String[] {
      Images.ImageColumns.ORIENTATION
  };

  MediaStoreBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(context, picasso, dispatcher, cache, stats, action);
  }

  @Override Bitmap decode(Request data) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    setExifRotation(getExifOrientation(contentResolver, data.uri));
    String mimeType = contentResolver.getType(data.uri);
    boolean isVideo = mimeType != null && mimeType.startsWith("video/");

    if (data.hasSize()) {
      PicassoKind picassoKind = getPicassoKind(data.targetWidth, data.targetHeight);
      if (!isVideo && picassoKind == FULL) {
        return super.decode(data);
      }

      long id = parseId(data.uri);

      BitmapFactory.Options options = createBitmapOptions(data);
      options.inJustDecodeBounds = true;

      calculateInSampleSize(data.targetWidth, data.targetHeight, picassoKind.width,
          picassoKind.height, options);

      Bitmap result;

      if (isVideo) {
        // Since MediaStore doesn't provide the full screen kind thumbnail, we use the mini kind
        // instead which is the largest thumbnail size can be fetched from MediaStore.
        int kind = (picassoKind == FULL) ? Video.Thumbnails.MINI_KIND : picassoKind.androidKind;
        result = Video.Thumbnails.getThumbnail(contentResolver, id, kind, options);
      } else {
        result =
            Images.Thumbnails.getThumbnail(contentResolver, id, picassoKind.androidKind, options);
      }

      if (result != null) {
        return result;
      }
    }

    return super.decode(data);
  }

  static PicassoKind getPicassoKind(int targetWidth, int targetHeight) {
    if (targetWidth <= MICRO.width && targetHeight <= MICRO.height) {
      return MICRO;
    } else if (targetWidth <= MINI.width && targetHeight <= MINI.height) {
      return MINI;
    }
    return FULL;
  }

  static int getExifOrientation(ContentResolver contentResolver, Uri uri) {
    Cursor cursor = null;
    try {
      cursor = contentResolver.query(uri, CONTENT_ORIENTATION, null, null, null);
      if (cursor == null || !cursor.moveToFirst()) {
        return 0;
      }
      return cursor.getInt(0);
    } catch (RuntimeException ignored) {
      // If the orientation column doesn't exist, assume no rotation.
      return 0;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
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
