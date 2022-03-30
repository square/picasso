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

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract.Contacts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.QuickContactBadge
import android.widget.TextView
import androidx.cursoradapter.widget.CursorAdapter
import com.example.picasso.SampleContactsActivity.ContactsQuery

internal class SampleContactsAdapter(context: Context) : CursorAdapter(context, null, 0) {
  private val inflater = LayoutInflater.from(context)

  override fun newView(
    context: Context,
    cursor: Cursor,
    viewGroup: ViewGroup
  ): View {
    val itemLayout = inflater.inflate(R.layout.sample_contacts_activity_item, viewGroup, false)
    itemLayout.tag = ViewHolder(
      text1 = itemLayout.findViewById(android.R.id.text1),
      icon = itemLayout.findViewById(android.R.id.icon)
    )
    return itemLayout
  }

  override fun bindView(
    view: View,
    context: Context,
    cursor: Cursor
  ) {
    val contactUri = Contacts.getLookupUri(
      cursor.getLong(ContactsQuery.ID),
      cursor.getString(ContactsQuery.LOOKUP_KEY)
    )
    val holder = (view.tag as ViewHolder).apply {
      text1.text = cursor.getString(ContactsQuery.DISPLAY_NAME)
      icon.assignContactUri(contactUri)
    }

    PicassoInitializer.get()
      .load(contactUri)
      .placeholder(R.drawable.contact_picture_placeholder)
      .tag(context)
      .into(holder.icon)
  }

  override fun getCount(): Int {
    return if (cursor == null) 0 else super.getCount()
  }

  private class ViewHolder(
    val text1: TextView,
    val icon: QuickContactBadge
  )
}
