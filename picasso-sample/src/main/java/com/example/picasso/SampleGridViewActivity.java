package com.example.picasso;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.GridView;

public class SampleGridViewActivity extends PicassoSampleActivity {
  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_gridview_activity);

    GridView gv = findViewById(R.id.grid_view);
    gv.setAdapter(new SampleGridViewAdapter(this));
    gv.setOnScrollListener(new SampleScrollListener(this));
  }
}
