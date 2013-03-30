package com.squareup.picasso.sample;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import com.squareup.picasso.Picasso;

public class SampleActivity extends Activity {
  private SampleAdapter adapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    StrictMode.setThreadPolicy(
        new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());

    adapter = new SampleAdapter(this);

    ListView lv = new ListView(this);
    lv.setAdapter(adapter);

    setContentView(lv);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == 0) {
      item.setChecked(!item.isChecked());

      Picasso.with(this).setDebugging(item.isChecked());
      adapter.notifyDataSetChanged();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    MenuItem debugItem = menu.add(0, 0, 0, "Debugging");
    debugItem.setCheckable(true);
    debugItem.setChecked(Picasso.with(this).isDebugging());
    return super.onCreateOptionsMenu(menu);
  }
}
