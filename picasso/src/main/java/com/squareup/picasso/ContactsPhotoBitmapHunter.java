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

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import java.io.IOException;
import java.io.InputStream;

import static android.provider.ContactsContract.Contacts.openContactPhotoInputStream;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class ContactsPhotoBitmapHunter extends BitmapHunter {
  final Context context;

  ContactsPhotoBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(picasso, dispatcher, cache, stats, action);
    this.context = context;
  }

  @Override Bitmap decode(Request data)
      throws IOException {
    InputStream is = null;
    try {
      is = getInputStream();
      return decodeStream(is, data);
    } finally {
      Utils.closeQuietly(is);
    }
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  private InputStream getInputStream() throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    Uri uri = getData().uri;
    if (uri.toString().startsWith(ContactsContract.Contacts.CONTENT_LOOKUP_URI.toString())) {
      uri = ContactsContract.Contacts.lookupContact(contentResolver, uri);
      if (uri == null) {
        return null;
      }
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      return openContactPhotoInputStream(contentResolver, uri);
    } else {
      return ContactPhotoStreamIcs.get(contentResolver, uri);
    }
  }

  private Bitmap decodeStream(InputStream stream, Request data) throws IOException {
    if (stream == null) {
      return null;
    }
    BitmapFactory.Options options = null;
    if (data.hasSize()) {
      options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      InputStream is = getInputStream();
      try {
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(data.targetWidth, data.targetHeight, options);
    }
    return BitmapFactory.decodeStream(stream, null, options);
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private static class ContactPhotoStreamIcs {
    static InputStream get(ContentResolver contentResolver, Uri uri) {
      return openContactPhotoInputStream(contentResolver, uri, true);
    }
  }
}
