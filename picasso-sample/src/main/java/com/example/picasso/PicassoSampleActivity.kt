package com.example.picasso

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.AdapterView.OnItemClickListener
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ToggleButton
import androidx.fragment.app.FragmentActivity
import com.example.picasso.provider.PicassoProvider

abstract class PicassoSampleActivity : FragmentActivity() {
  private lateinit var sampleContent: FrameLayout
  private lateinit var showHide: ToggleButton

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    super.setContentView(R.layout.picasso_sample_activity)

    sampleContent = findViewById(R.id.sample_content)

    val activityList = findViewById<ListView>(R.id.activity_list)
    val adapter = PicassoSampleAdapter(this)
    activityList.adapter = adapter
    activityList.onItemClickListener = OnItemClickListener { _, _, position, _ ->
      adapter.getItem(position).launch(this@PicassoSampleActivity)
    }
    
    showHide = findViewById(R.id.faux_action_bar_control)
    showHide.setOnCheckedChangeListener { _, checked ->
      activityList.visibility = if (checked) View.VISIBLE else View.GONE
    }

    lifecycle.addObserver(PicassoProvider.get())
  }

  override fun onBackPressed() {
    if (showHide.isChecked) {
      showHide.isChecked = false
    } else {
      super.onBackPressed()
    }
  }

  override fun setContentView(layoutResID: Int) {
    layoutInflater.inflate(layoutResID, sampleContent)
  }

  override fun setContentView(view: View) {
    sampleContent.addView(view)
  }

  override fun setContentView(view: View, params: LayoutParams) {
    sampleContent.addView(view, params)
  }
}