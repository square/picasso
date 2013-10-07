package com.example.picasso;

import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ToggleButton;

public class SampleGridViewActivity extends PicassoSampleActivity {
  private ToggleButton showRound;
  private SampleGridViewAdapter gridAdapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_gridview_activity);

    showRound = (ToggleButton) findViewById(R.id.faux_action_bar_round);
    showRound.setVisibility(View.VISIBLE);
    showRound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
        gridAdapter.setRoundEffect(checked);
        gridAdapter.notifyDataSetChanged();
      }
    });

    GridView gv = (GridView) findViewById(R.id.grid_view);

    gridAdapter = new SampleGridViewAdapter(this);
    gv.setAdapter(gridAdapter);
  }
}
