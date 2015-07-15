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
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.provider.MediaStore.Images;
import static android.provider.MediaStore.Images.Thumbnails.FULL_SCREEN_KIND;
import static android.provider.MediaStore.Images.Thumbnails.MICRO_KIND;
import static android.provider.MediaStore.Images.Thumbnails.MINI_KIND;
import static android.provider.MediaStore.Video;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.FULL;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.MICRO;
import static com.squareup.picasso.MediaStoreRequestHandler.PicassoKind.MINI;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class MediaStoreRequestHandler extends ContentStreamRequestHandler {
  private static final String[] CONTENT_ORIENTATION = new String[] {
          Images.ImageColumns.ORIENTATION,
  };
  private static final String[] CONTENT_DATA = new String[] {
          Images.Media.DATA
  };

  // KitKat media uris
  private static final String MEDIA_DOCUMENTS_PROVIDER = "com.android.providers.media.documents";

  MediaStoreRequestHandler(Context context) {
    super(context);
  }

  @Override public boolean canHandleRequest(Request data) {
    final Uri uri = data.uri;
    final String authority = uri.getAuthority();

    return SCHEME_CONTENT.equals(uri.getScheme())
            && (MediaStore.AUTHORITY.equals(authority)
            || MEDIA_DOCUMENTS_PROVIDER.equals(authority));
  }

  @Override public Result load(Request request, int networkPolicy) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    int exifOrientation = getExifOrientation(contentResolver, request.uri);

    boolean isVideo = isVideo(contentResolver, request.uri);

    if (request.hasSize()) {
      PicassoKind picassoKind = getPicassoKind(request.targetWidth, request.targetHeight);

      if (!isVideo && picassoKind == FULL) {
        return new Result(null, getInputStream(request), DISK, exifOrientation);
      }

      long id = parseId(request.uri);

      BitmapFactory.Options options = createBitmapOptions(request);
      options.inJustDecodeBounds = true;

      calculateInSampleSize(request.targetWidth, request.targetHeight, picassoKind.width,
              picassoKind.height, options, request);

      Bitmap bitmap;

      if (isVideo) {
        // Since MediaStore doesn't provide the full screen kind thumbnail, we use the mini kind
        // instead which is the largest thumbnail size can be fetched from MediaStore.
        int kind = (picassoKind == FULL) ? Video.Thumbnails.MINI_KIND : picassoKind.androidKind;
        bitmap = Video.Thumbnails.getThumbnail(contentResolver, id, kind, options);
      } else {
        bitmap = Images.Thumbnails.getThumbnail(contentResolver, id,
                picassoKind.androidKind, options);
      }

      if (bitmap != null) {
        return new Result(bitmap, null, DISK, exifOrientation);
      }
    }

    return new Result(null, getInputStream(request), DISK, exifOrientation);
  }

  // example uris:
  //  content://media/external/images/media/42
  //  content://com.android.providers.media.documents/document/image:42  (KitKat)
  static long parseId(Uri uri) {
    // handles image:xx uris
    return Long.parseLong(uri.getLastPathSegment().replaceAll("\\D", ""));
  }

  static boolean isVideo(ContentResolver contentResolver, Uri uri) {
    String mimeType = contentResolver.getType(uri);
    return mimeType != null && mimeType.startsWith("video/");
  }

  static PicassoKind getPicassoKind(int targetWidth, int targetHeight) {
    if (targetWidth <= MICRO.width && targetHeight <= MICRO.height) {
      return MICRO;
    } else if (targetWidth <= MINI.width && targetHeight <= MINI.height) {
      return MINI;
    }
    return FULL;
  }

  int getExifOrientation(ContentResolver contentResolver, Uri uri) {
    int orientation = getExifOrientationFromContentResolver(contentResolver, uri);
    if (orientation == ExifInterface.ORIENTATION_UNDEFINED) {
      // attempt to get exif orientation from file
      orientation = getExifOrientationFromFile(contentResolver, uri);
    }
    return orientation;
  }

  int getExifOrientationFromContentResolver(ContentResolver contentResolver, Uri uri) {
    String orientationStr = queryContentResolver(contentResolver, uri, CONTENT_ORIENTATION, null,
            new ContentResolverCursorCallback() {
              @Override
              public String handleCursor(Cursor cursor) {
                return cursor.getString(0);
              }
            });
    return orientationStr == null ? 0 : Integer.parseInt(orientationStr);
  }

  int getExifOrientationFromFile(ContentResolver contentResolver, Uri uri) {
    int orientation = 0;
    String path = getMediaPath(contentResolver, uri);
    if (path != null) {
      try {
        ExifInterface exif = new ExifInterface(path);
        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
      } catch (IOException e) {
        // thrown on ExifInterface error
        Utils.log(MediaStoreRequestHandler.class.getSimpleName(), Utils.VERB_ERRORED, "");
      }
    }
    return orientation;
  }

  String getMediaPath(ContentResolver contentResolver, Uri uri) {
    boolean isVideo = isVideo(contentResolver, uri);
    Uri baseUri = isVideo ? Video.Media.EXTERNAL_CONTENT_URI : Images.Media.EXTERNAL_CONTENT_URI;
    long id = parseId(uri);

    return queryContentResolver(contentResolver, baseUri, CONTENT_DATA, "_id=" + id,
            new ContentResolverCursorCallback() {
              @Override
              public String handleCursor(Cursor cursor) {
                return cursor.getString(0);
              }
            });
  }

  String queryContentResolver(ContentResolver contentResolver, Uri uri, String[] projection,
                            String selection, ContentResolverCursorCallback callback) {
    Cursor cursor = null;

    try {
      cursor = contentResolver.query(uri, projection, selection, null, null);
      if (cursor != null && cursor.moveToFirst() && callback != null) {
        return callback.handleCursor(cursor);
      }
    } catch (Exception e) {
      Utils.log(MediaStoreRequestHandler.class.getSimpleName(), Utils.VERB_ERRORED, "");
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return null;
  }

  interface ContentResolverCursorCallback {
    String handleCursor(Cursor cursor);
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
