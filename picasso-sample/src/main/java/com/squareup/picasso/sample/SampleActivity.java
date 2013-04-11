package com.squareup.picasso.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;
import com.squareup.picasso.Picasso;

import static android.widget.Toast.LENGTH_SHORT;

public class SampleActivity extends Activity {
  private SampleAdapter adapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_activity);

    adapter = new SampleAdapter(this);
    GridView gv = (GridView) findViewById(R.id.grid_view);
    gv.setAdapter(adapter);
    gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int position, long itemId) {
        Toast.makeText(SampleActivity.this, adapter.getItem(position), LENGTH_SHORT).show();
      }
    });
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
