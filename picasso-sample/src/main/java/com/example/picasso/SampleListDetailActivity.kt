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
import com.example.picasso.provider.PicassoProvider

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

      val url = arguments!!.getString(KEY_URL)
      urlView.text = url
      PicassoProvider.get()
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