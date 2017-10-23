package com.example.picasso;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

public class PicassoTextActivity extends PicassoSampleActivity {

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_text_activity);

    Picasso.with()
            .load("https://www.smashingmagazine.com/wp-content/uploads/2015/06/10-dithering-opt.jpg")
            .placeholder("PL", Color.RED, Color.BLACK)
            .error("Error", Color.WHITE)
            .fit()
            .tag(this)
            .into((ImageView) findViewById(R.id.image1));

    Picasso.with()
            .load("https://www.error.com/link")
            .placeholder("PL", Color.RED, Color.BLACK)
            .error("Error", Color.WHITE)
            .fit()
            .tag(this)
            .into((ImageView) findViewById(R.id.image2));

  }
}
