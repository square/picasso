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
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView.ScaleType.CENTER_CROP

internal class SampleGridViewAdapter(private val context: Context) : BaseAdapter() {
  private val urls: List<String>

  init {
    // Ensure we get a different ordering of images on each run.
    val tmpList = Data.URLS.toMutableList()
    tmpList.shuffle()

    // Triple up the list.
    urls = listOf(tmpList, tmpList, tmpList).flatten()
  }

  override fun getView(
    position: Int,
    convertView: View?,
    parent: ViewGroup
  ): View {
    val view = convertView as? SquaredImageView ?: SquaredImageView(context).apply {
      scaleType = CENTER_CROP
    }

    // Get the image URL for the current position.
    val url = getItem(position)

    // Trigger the download of the URL asynchronously into the image view.
    PicassoInitializer.get()
      .load(url)
      .placeholder(R.drawable.placeholder)
      .error(R.drawable.error)
      .fit()
      .tag(context)
      .into(view)

    return view
  }

  override fun getCount(): Int = urls.size

  override fun getItem(position: Int): String = urls[position]

  override fun getItemId(position: Int): Long = position.toLong()
}
