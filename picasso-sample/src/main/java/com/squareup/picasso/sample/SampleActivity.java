package com.squareup.picasso.sample;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.widget.ListView;

public class SampleActivity extends Activity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    StrictMode.setThreadPolicy(
        new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectAll().penaltyLog().build());

    ListView lv = new ListView(this);
    lv.setAdapter(new SampleAdapter(this));
    setContentView(lv);
  }
}
