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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment

class SampleListDetailActivity : PicassoSampleActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      supportFragmentManager
        .beginTransaction()
        .add(R.id.sample_content, ListFragment.newInstance())
        .commit()
    }
  }

  fun showDetails(url: String) {
    supportFragmentManager
      .beginTransaction()
      .replace(R.id.sample_content, DetailFragment.newInstance(url))
      .addToBackStack(null)
      .commit()
  }

  class ListFragment : Fragment() {
    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View? {
      val activity = activity as SampleListDetailActivity
      val adapter = SampleListDetailAdapter(activity)
      val listView = LayoutInflater.from(activity)
        .inflate(R.layout.sample_list_detail_list, container, false) as ListView

      listView.adapter = adapter
      listView.setOnScrollListener(SampleScrollListener(activity))
      listView.onItemClickListener = OnItemClickListener { _, _, position, _ ->
        val url = adapter.getItem(position)
        activity.showDetails(url)
      }

      return listView
    }

    companion object {
      fun newInstance(): ListFragment {
        return ListFragment()
      }
    }
  }

  class DetailFragment : Fragment() {
    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View? {
      val activity = activity as SampleListDetailActivity
      val view = LayoutInflater.from(activity)
        .inflate(R.layout.sample_list_detail_detail, container, false)

      val urlView = view.findViewById<TextView>(R.id.url)
      val imageView = view.findViewById<ImageView>(R.id.photo)

      val url = requireArguments().getString(KEY_URL)
      urlView.text = url
      PicassoInitializer.get()
        .load(url)
        .fit()
        .tag(activity)
        .into(imageView)

      return view
    }

    companion object {
      private const val KEY_URL = "picasso:url"

      fun newInstance(url: String): DetailFragment {
        return DetailFragment().apply {
          arguments = Bundle().apply { putString(KEY_URL, url) }
        }
      }
    }
  }
}
