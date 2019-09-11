package com.example.picasso

import android.content.Context
import android.widget.AbsListView
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL
import com.example.picasso.provider.PicassoProvider

class SampleScrollListener(private val context: Context) : AbsListView.OnScrollListener {

  override fun onScrollStateChanged(
    view: AbsListView,
    scrollState: Int
  ) {
    val picasso = PicassoProvider.get()
    when (scrollState) {
      SCROLL_STATE_IDLE, SCROLL_STATE_TOUCH_SCROLL -> picasso.resumeTag(context)
      else -> picasso.pauseTag(context)
    }
  }

  override fun onScroll(
    view: AbsListView,
    firstVisibleItem: Int,
    visibleItemCount: Int,
    totalItemCount: Int
  ) = Unit
}
