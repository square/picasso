package com.squareup.picasso.sample;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;
import com.squareup.picasso.Picasso;

import static android.app.ActionBar.DISPLAY_SHOW_TITLE;
import static android.view.Window.FEATURE_ACTION_BAR_OVERLAY;

public class SampleActivity extends Activity {
  private SampleAdapter adapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(FEATURE_ACTION_BAR_OVERLAY);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_activity);

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
      ActionBar actionBar = getActionBar();
      actionBar.setDisplayOptions(DISPLAY_SHOW_TITLE);
      actionBar.setBackgroundDrawable(new ColorDrawable(0x50000000));
    }

    adapter = new SampleAdapter(this);
    GridView gv = (GridView) findViewById(R.id.grid_view);
    gv.setAdapter(adapter);
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    menu.add("Debugging")
        .setCheckable(true)
        .setChecked(Picasso.with(this).isDebugging())
        .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
          @Override public boolean onMenuItemClick(MenuItem item) {
            item.setChecked(!item.isChecked());
            Picasso.with(SampleActivity.this).setDebugging(item.isChecked());
            adapter.notifyDataSetChanged();
            return true;
          }
        });
    return true;
  }
}
