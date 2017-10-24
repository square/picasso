package com.example.picasso;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.TextDrawable;

public class PicassoTextActivity extends PicassoSampleActivity {

  private final int[] gravities = {
      Gravity.TOP | Gravity.LEFT,
      Gravity.TOP | Gravity.CENTER,
      Gravity.TOP | Gravity.RIGHT,
      Gravity.CENTER | Gravity.LEFT,
      Gravity.CENTER,
      Gravity.CENTER | Gravity.RIGHT,
      Gravity.BOTTOM | Gravity.LEFT,
      Gravity.BOTTOM | Gravity.CENTER,
      Gravity.BOTTOM | Gravity.RIGHT};

  private ImageView image;

  private int gravityIndex = 0;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_text_activity);

    image = findViewById(R.id.image);

    reload();

    findViewById(R.id.previousTextGravity).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        gravityIndex = gravities.length - (Math.abs(gravityIndex--) % gravities.length) - 1;
        reload();
      }
    });

    findViewById(R.id.nextTextGravity).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        gravityIndex = (gravityIndex + 1) % gravities.length;
        reload();
      }
    });
  }

  private void reload() {
    Picasso.with()
        .load("https://www.error.com/link")
        .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
        .error(new TextDrawable.Builder("Error")
            .setTextGravity(gravities[gravityIndex]))
        .tag(this)
        .into(image);
  }
}
