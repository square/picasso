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
package com.squareup.picasso3

import android.content.ContentResolver
import android.content.Context
import android.content.UriMatcher
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import com.squareup.picasso3.BitmapUtils.decodeStream
import com.squareup.picasso3.Picasso.LoadedFrom.DISK
import okio.Source
import okio.source
import java.io.FileNotFoundException
import java.io.IOException

internal class ContactsPhotoRequestHandler(private val context: Context) : RequestHandler() {
  companion object {
    /** A lookup uri (e.g. content://com.android.contacts/contacts/lookup/3570i61d948d30808e537) */
    private const val ID_LOOKUP = 1
    /** A contact thumbnail uri (e.g. content://com.android.contacts/contacts/38/photo) */
    private const val ID_THUMBNAIL = 2
    /** A contact uri (e.g. content://com.android.contacts/contacts/38) */
    private const val ID_CONTACT = 3
    /**
     * A contact display photo (high resolution) uri
     * (e.g. content://com.android.contacts/display_photo/5)
     */
    private const val ID_DISPLAY_PHOTO = 4

    private val matcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
      addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#", ID_LOOKUP)
      addURI(ContactsContract.AUTHORITY, "contacts/lookup/*", ID_LOOKUP)
      addURI(ContactsContract.AUTHORITY, "contacts/#/photo", ID_THUMBNAIL)
      addURI(ContactsContract.AUTHORITY, "contacts/#", ID_CONTACT)
      addURI(ContactsContract.AUTHORITY, "display_photo/#", ID_DISPLAY_PHOTO)
    }
  }

  override fun canHandleRequest(data: Request): Boolean {
    val uri = data.uri
    return uri != null &&
      ContentResolver.SCHEME_CONTENT == uri.scheme &&
      Contacts.CONTENT_URI.host == uri.host &&
      matcher.match(data.uri) != UriMatcher.NO_MATCH
  }

  override fun load(
    picasso: Picasso,
    request: Request,
    callback: Callback
  ) {
    var signaledCallback = false
    try {
      val requestUri = checkNotNull(request.uri)
      val source = getSource(requestUri)
      val bitmap = decodeStream(source, request)
      signaledCallback = true
      callback.onSuccess(Result.Bitmap(bitmap, DISK))
    } catch (e: Exception) {
      if (!signaledCallback) {
        callback.onError(e)
      }
    }
  }

  private fun getSource(uri: Uri): Source {
    val contentResolver = context.contentResolver
    val input = when (matcher.match(uri)) {
      ID_LOOKUP -> {
        val contactUri =
          Contacts.lookupContact(contentResolver, uri) ?: throw IOException("no contact found")
        Contacts.openContactPhotoInputStream(contentResolver, contactUri, true)
      }
      ID_CONTACT -> Contacts.openContactPhotoInputStream(contentResolver, uri, true)
      ID_THUMBNAIL, ID_DISPLAY_PHOTO -> contentResolver.openInputStream(uri)
      else -> throw IllegalStateException("Invalid uri: $uri")
    } ?: throw FileNotFoundException("can't open input stream, uri: $uri")

    return input.source()
  }
}
