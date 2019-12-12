package com.example.picasso

import android.os.Bundle
import android.widget.GridView

class SampleGridViewActivity : PicassoSampleActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.sample_gridview_activity)

    findViewById<GridView>(R.id.grid_view).apply {
      adapter = SampleGridViewAdapter(this@SampleGridViewActivity)
      setOnScrollListener(SampleScrollListener(this@SampleGridViewActivity))
    }
  }
}
