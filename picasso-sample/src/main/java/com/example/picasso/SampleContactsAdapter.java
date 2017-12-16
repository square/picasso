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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.squareup.picasso.Picasso;

import static android.provider.ContactsContract.Contacts;
import static com.example.picasso.SampleContactsActivity.ContactsQuery;

class SampleContactsAdapter extends CursorAdapter {
  private final LayoutInflater inflater;

  SampleContactsAdapter(Context context) {
    super(context, null, 0);
    inflater = LayoutInflater.from(context);
  }

  @Override public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
    View itemLayout = inflater.inflate(R.layout.sample_contacts_activity_item, viewGroup, false);

    ViewHolder holder = new ViewHolder();
    holder.text1 = (TextView) itemLayout.findViewById(android.R.id.text1);
    holder.icon = (QuickContactBadge) itemLayout.findViewById(android.R.id.icon);

    itemLayout.setTag(holder);

    return itemLayout;
  }

  @Override public void bindView(View view, Context context, Cursor cursor) {
    Uri contactUri = Contacts.getLookupUri(cursor.getLong(ContactsQuery.ID),
        cursor.getString(ContactsQuery.LOOKUP_KEY));

    ViewHolder holder = (ViewHolder) view.getTag();
    holder.text1.setText(cursor.getString(ContactsQuery.DISPLAY_NAME));
    holder.icon.assignContactUri(contactUri);

    Picasso.get()
        .load(contactUri)
        .placeholder(R.drawable.contact_picture_placeholder)
        .tag(context)
        .into(holder.icon);
  }

  @Override public int getCount() {
    return getCursor() == null ? 0 : super.getCount();
  }

  private static class ViewHolder {
    TextView text1;
    QuickContactBadge icon;
  }
}
