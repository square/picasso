package com.squareup.picasso.sample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;

public class SampleActivity extends Activity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ListView lv = new ListView(this);
    lv.setAdapter(new SampleAdapter(this));
    setContentView(lv);
  }
}
