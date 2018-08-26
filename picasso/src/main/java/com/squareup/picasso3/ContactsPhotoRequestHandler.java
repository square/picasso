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
import android.content.UriMatcher;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import okio.Okio;
import okio.Source;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.provider.ContactsContract.Contacts.openContactPhotoInputStream;
import static com.squareup.picasso3.BitmapUtils.decodeStream;
import static com.squareup.picasso3.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso3.Utils.checkNotNull;

class ContactsPhotoRequestHandler extends RequestHandler {
  /** A lookup uri (e.g. content://com.android.contacts/contacts/lookup/3570i61d948d30808e537) */
  private static final int ID_LOOKUP = 1;
  /** A contact thumbnail uri (e.g. content://com.android.contacts/contacts/38/photo) */
  private static final int ID_THUMBNAIL = 2;
  /** A contact uri (e.g. content://com.android.contacts/contacts/38) */
  private static final int ID_CONTACT = 3;
  /**
   * A contact display photo (high resolution) uri
   * (e.g. content://com.android.contacts/display_photo/5)
   */
  private static final int ID_DISPLAY_PHOTO = 4;

  private static final UriMatcher matcher;

  static {
    matcher = new UriMatcher(UriMatcher.NO_MATCH);
    matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#", ID_LOOKUP);
    matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*", ID_LOOKUP);
    matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/photo", ID_THUMBNAIL);
    matcher.addURI(ContactsContract.AUTHORITY, "contacts/#", ID_CONTACT);
    matcher.addURI(ContactsContract.AUTHORITY, "display_photo/#", ID_DISPLAY_PHOTO);
  }

  private final Context context;

  ContactsPhotoRequestHandler(Context context) {
    this.context = context;
  }

  @Override public boolean canHandleRequest(@NonNull Request data) {
    final Uri uri = data.uri;
    return uri != null
        && SCHEME_CONTENT.equals(uri.getScheme())
        && ContactsContract.Contacts.CONTENT_URI.getHost().equals(uri.getHost())
        && matcher.match(data.uri) != UriMatcher.NO_MATCH;
  }

  @Override
  public void load(@NonNull Picasso picasso, @NonNull Request request, @NonNull Callback callback) {
    boolean signaledCallback = false;
    try {
      Uri requestUri = checkNotNull(request.uri, "request.uri == null");
      Source source = getSource(requestUri);
      Bitmap bitmap = decodeStream(source, request);
      signaledCallback = true;
      callback.onSuccess(new Result(bitmap, DISK));
    } catch (Exception e) {
      if (!signaledCallback) {
        callback.onError(e);
      }
    }
  }

  private Source getSource(Uri uri) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    InputStream is;
    switch (matcher.match(uri)) {
      case ID_LOOKUP:
        uri = ContactsContract.Contacts.lookupContact(contentResolver, uri);
        if (uri == null) {
          throw new IOException("no contact found");
        }
        // Resolved the uri to a contact uri, intentionally fall through to process the resolved uri
      case ID_CONTACT:
        is = openContactPhotoInputStream(contentResolver, uri, true);
        break;
      case ID_THUMBNAIL:
      case ID_DISPLAY_PHOTO:
        is = contentResolver.openInputStream(uri);
        break;
      default:
        throw new IllegalStateException("Invalid uri: " + uri);
    }
    if (is == null) {
      throw new FileNotFoundException("can't open input stream, uri: " + uri);
    }

    return Okio.source(is);
  }
}
