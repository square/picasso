/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.example.picasso;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.widget.ListView;
import android.widget.Toast;

import static android.provider.ContactsContract.Contacts;

public class SampleContactsActivity extends PicassoSampleActivity
    implements LoaderManager.LoaderCallbacks<Cursor> {
  private static final boolean IS_HONEYCOMB =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;

  private final int permissionReadContacts = 123;

  private SampleContactsAdapter adapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_contacts_activity);

    if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
              new String[]{Manifest.permission.READ_CONTACTS},
              permissionReadContacts);
    }

    adapter = new SampleContactsAdapter(this);

    ListView lv = (ListView) findViewById(android.R.id.list);
    lv.setAdapter(adapter);
    lv.setOnScrollListener(new SampleScrollListener(this));

    getSupportLoaderManager().initLoader(ContactsQuery.QUERY_ID, null, this);
  }

  @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    if (id == ContactsQuery.QUERY_ID) {
      return new CursorLoader(this, //
          ContactsQuery.CONTENT_URI, //
          ContactsQuery.PROJECTION, //
          ContactsQuery.SELECTION, //
          null, //
          ContactsQuery.SORT_ORDER);
    }
    return null;
  }

  @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    adapter.swapCursor(data);
  }

  @Override public void onLoaderReset(Loader<Cursor> loader) {
    adapter.swapCursor(null);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {

    switch (requestCode) {
      case permissionReadContacts:
        if (grantResults.length < 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
          Toast.makeText(getApplicationContext(),
                  "Contact Read Permission is required to access photos.",
                  Toast.LENGTH_LONG).show();
        }
        break;
      default:
        break;
    }
  }

  interface ContactsQuery {
    int QUERY_ID = 1;

    Uri CONTENT_URI = Contacts.CONTENT_URI;

    String SELECTION = (IS_HONEYCOMB ? Contacts.DISPLAY_NAME_PRIMARY : Contacts.DISPLAY_NAME)
        + "<>''"
        + " AND "
        + Contacts.IN_VISIBLE_GROUP
        + "=1";

    String SORT_ORDER = IS_HONEYCOMB ? Contacts.SORT_KEY_PRIMARY : Contacts.DISPLAY_NAME;

    String[] PROJECTION = {
        Contacts._ID, //
        Contacts.LOOKUP_KEY, //
        IS_HONEYCOMB ? Contacts.DISPLAY_NAME_PRIMARY : Contacts.DISPLAY_NAME, //
        IS_HONEYCOMB ? Contacts.PHOTO_THUMBNAIL_URI : Contacts._ID, //
        SORT_ORDER
    };

    int ID = 0;
    int LOOKUP_KEY = 1;
    int DISPLAY_NAME = 2;
  }
}
