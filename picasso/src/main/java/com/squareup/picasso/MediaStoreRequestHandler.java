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

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.content.ContentUris.parseId;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.GINGERBREAD_MR1;
import static android.provider.MediaStore.Images;
import static android.provider.MediaStore.Images.Thumbnails.FULL_SCREEN_KIND;
import static android.provider.MediaStore.Images.Thumbnails.MICRO_KIND;
import static android.provider.MediaStore.Images.Thumbnails.MINI_KIND;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.FULL;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.MICRO;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.MINI;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class MediaStoreRequestHandler extends ContentStreamRequestHandler {
  private static final String[] CONTENT_ORIENTATION = new String[] {
      Images.ImageColumns.ORIENTATION
  };
  static final String TIME_OFFSET_QUERY = "t";

  MediaStoreRequestHandler(Context context) {
    super(context);
  }

  @Override public boolean canHandleRequest(Request data) {
    final Uri uri = data.uri;
    return (SCHEME_CONTENT.equals(uri.getScheme())
            && MediaStore.AUTHORITY.equals(uri.getAuthority()));
  }

  @Override public Result load(Request data) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    int exifOrientation = getExifOrientation(contentResolver, data.uri);

    String mimeType = contentResolver.getType(data.uri);
    boolean isVideo = mimeType != null && mimeType.startsWith("video/");

    if (data.hasSize()) {
      PicassoKind picassoKind = getPicassoKind(data.targetWidth, data.targetHeight);
      if (!isVideo && picassoKind == FULL) {
        return new Result(decodeContentStream(data), DISK, exifOrientation);
      }

      long id = parseId(data.uri);

      BitmapFactory.Options options = createBitmapOptions(data);
      options.inJustDecodeBounds = true;

      calculateInSampleSize(data.targetWidth, data.targetHeight, picassoKind.width,
              picassoKind.height, options);

      Bitmap bitmap;

      if (isVideo) {
        long offsetMicros = Long.MIN_VALUE;
        try {
          // Attempt to retrieve a time offset from the URI in microseconds.
          String timeParameter = data.uri.getQueryParameter(TIME_OFFSET_QUERY);
          offsetMicros = Long.valueOf(timeParameter);
        } catch (NumberFormatException ignored) {
        } catch (UnsupportedOperationException ignored) { }

        if (SDK_INT >= GINGERBREAD_MR1 && offsetMicros >= 0) {
          bitmap = getVideoFrame(context, data.uri, offsetMicros);
        } else {
          // Android's default thumbnail behavior is to pull a frame from the middle of the video.
          // Since MediaStore doesn't provide the full screen kind thumbnail, we use the mini kind
          // instead which is the largest thumbnail size can be fetched from MediaStore.
          int kind = (picassoKind == FULL) ? MediaStore.Video.Thumbnails.MINI_KIND
                  : picassoKind.androidKind;
          bitmap = MediaStore.Video.Thumbnails.getThumbnail(contentResolver, id, kind, options);
        }
      } else {
        bitmap = Images.Thumbnails
                .getThumbnail(contentResolver, id, picassoKind.androidKind, options);
      }

      if (bitmap != null) {
        return new Result(bitmap, DISK, exifOrientation);
      }
    }

    return new Result(decodeContentStream(data), DISK, exifOrientation);
  }

  @TargetApi(GINGERBREAD_MR1)
  private Bitmap getVideoFrame(Context context, Uri uri, long time) {
    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
    mediaMetadataRetriever.setDataSource(context, uri);
    return mediaMetadataRetriever.getFrameAtTime(time);
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
