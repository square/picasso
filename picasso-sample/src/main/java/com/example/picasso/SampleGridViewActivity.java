package com.example.picasso;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import android.widget.GridView;
import com.squareup.picasso.Picasso;

public class SampleGridViewActivity extends PicassoSampleActivity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_gridview_activity);

    Point displaySize = getDisplaySize();
    int size = displaySize.x / getResources().getInteger(R.integer.column_count);

    GridView gv = (GridView) findViewById(R.id.grid_view);
    gv.setAdapter(new SampleGridViewAdapter(this, size));

    gv.postDelayed(new Runnable() {
      @Override
      public void run() {
        Picasso.with(SampleGridViewActivity.this).shutdown();
      }
    }, 10000);
  }

  private Point getDisplaySize() {
    WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    Point size = new Point();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
      display.getSize(size);
    } else {
      //noinspection deprecation
      size.x = display.getWidth();
      //noinspection deprecation
      size.y = display.getHeight();
    }
    return size;
  }
}
