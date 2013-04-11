package com.squareup.picasso.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;
import com.squareup.picasso.Picasso;

public class SampleActivity extends Activity {
  private SampleAdapter adapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_activity);

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
