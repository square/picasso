/*
 * Copyright (C) 2022 Square, Inc.
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

internal class SampleListDetailAdapter(private val context: Context) : BaseAdapter() {
  private val layoutInflater = LayoutInflater.from(context)
  private val urls = Data.URLS.toList()

  override fun getView(
    position: Int,
    view: View?,
    parent: ViewGroup
  ): View {
    val newView: View
    val holder: ViewHolder
    if (view == null) {
      newView = layoutInflater.inflate(R.layout.sample_list_detail_item, parent, false)
      holder = ViewHolder(
        image = newView.findViewById(R.id.photo),
        text = newView.findViewById(R.id.url)
      )
      newView.tag = holder
    } else {
      newView = view
      holder = newView.tag as ViewHolder
    }

    // Get the image URL for the current position.
    val url = getItem(position)
    holder.text.text = url

    // Trigger the download of the URL asynchronously into the image view.
    PicassoInitializer.get()
      .load(url)
      .placeholder(R.drawable.placeholder)
      .error(R.drawable.error)
      .resizeDimen(R.dimen.list_detail_image_size, R.dimen.list_detail_image_size)
      .centerInside()
      .tag(context)
      .into(holder.image)

    return newView
  }

  override fun getCount(): Int = urls.size

  override fun getItem(position: Int): String = urls[position]

  override fun getItemId(position: Int): Long = position.toLong()

  internal class ViewHolder(
    val image: ImageView,
    val text: TextView
  )
}
