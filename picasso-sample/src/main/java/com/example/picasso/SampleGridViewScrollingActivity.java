package com.example.picasso;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;

import com.squareup.picasso.scrolling.PicassoScrollListener;

public class SampleGridViewScrollingActivity extends PicassoSampleActivity {

    private Button skipMemoryCacheButton;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_gridview_activity_scrolling);
    boolean skipMemory = true;

    GridView gv = (GridView) findViewById(R.id.grid_view);
    final SampleGridViewAdapter adapter = new SampleGridViewAdapter(this, skipMemory);
    gv.setAdapter(adapter);
    gv.setOnScrollListener(new PicassoScrollListener(this));
    skipMemoryCacheButton = (Button) findViewById(R.id.skipMemoryCacheButton);
    updateButton(skipMemory);
    skipMemoryCacheButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            boolean skip = adapter.toggleSkipMemoryCache();
            updateButton(skip);
        }
    });

    Toast.makeText(this, "Fades loaded images only if GridView is NOT scrolling and NOT flinging", Toast.LENGTH_SHORT).show();


  }

  private void updateButton(boolean skip ){
        skipMemoryCacheButton.setText("skip memory cache = "+skip);
  }

}
