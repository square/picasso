package com.example.picasso;

import android.os.Bundle;
import android.widget.GridView;

import static android.view.Window.FEATURE_ACTION_BAR_OVERLAY;

public class SampleGridViewActivity extends PicassoSampleActivity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(FEATURE_ACTION_BAR_OVERLAY);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_gridview_activity);

    GridView gv = (GridView) findViewById(R.id.grid_view);
    gv.setAdapter(new SampleImagesAdapter(this));
  }
}
