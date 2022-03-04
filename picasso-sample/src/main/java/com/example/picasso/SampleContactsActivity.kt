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
package com.example.picasso

import android.Manifest.permission.READ_CONTACTS
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.Contacts
import android.widget.ListView
import android.widget.Toast
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.loader.app.LoaderManager
import androidx.loader.app.LoaderManager.LoaderCallbacks
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader

class SampleContactsActivity : PicassoSampleActivity(), LoaderCallbacks<Cursor> {
  private lateinit var adapter: SampleContactsAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.sample_contacts_activity)

    adapter = SampleContactsAdapter(this)

    findViewById<ListView>(android.R.id.list).apply {
      adapter = this@SampleContactsActivity.adapter
      setOnScrollListener(SampleScrollListener(this@SampleContactsActivity))
    }

    if (checkSelfPermission(this, READ_CONTACTS) == PERMISSION_GRANTED) {
      loadContacts()
    } else {
      requestPermissions(this, arrayOf(READ_CONTACTS), REQUEST_READ_CONTACTS)
    }
  }

  private fun loadContacts() {
    LoaderManager.getInstance(this).initLoader(ContactsQuery.QUERY_ID, null, this)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    if (requestCode == REQUEST_READ_CONTACTS) {
      if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
        loadContacts()
      } else {
        Toast
          .makeText(this, "Read contacts permission denied", Toast.LENGTH_LONG)
          .show()
        finish()
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  override fun onCreateLoader(
    id: Int,
    args: Bundle?
  ): Loader<Cursor> {
    return if (id == ContactsQuery.QUERY_ID) {
      CursorLoader(
        this,
        ContactsQuery.CONTENT_URI,
        ContactsQuery.PROJECTION,
        ContactsQuery.SELECTION,
        null,
        ContactsQuery.SORT_ORDER
      )
    } else throw RuntimeException("this shouldn't happen")
  }

  override fun onLoadFinished(
    loader: Loader<Cursor>,
    data: Cursor
  ) {
    adapter.swapCursor(data)
  }

  override fun onLoaderReset(loader: Loader<Cursor>) {
    adapter.swapCursor(null)
  }

  internal interface ContactsQuery {
    companion object {
      const val QUERY_ID = 1

      val CONTENT_URI: Uri = Contacts.CONTENT_URI

      const val SELECTION =
        "${Contacts.DISPLAY_NAME_PRIMARY}<>'' AND ${Contacts.IN_VISIBLE_GROUP}=1"

      const val SORT_ORDER = Contacts.SORT_KEY_PRIMARY

      val PROJECTION = arrayOf(
        Contacts._ID,
        Contacts.LOOKUP_KEY,
        Contacts.DISPLAY_NAME_PRIMARY,
        Contacts.PHOTO_THUMBNAIL_URI,
        SORT_ORDER
      )

      const val ID = 0
      const val LOOKUP_KEY = 1
      const val DISPLAY_NAME = 2
    }
  }

  companion object {
    private const val REQUEST_READ_CONTACTS = 123
  }
}
