package com.example.picasso;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.TextDrawable;

public class PicassoTextActivity extends PicassoSampleActivity {

  private final int[] gravities = {
      Gravity.TOP | Gravity.LEFT,
      Gravity.TOP | Gravity.CENTER,
      Gravity.TOP | Gravity.END,
      Gravity.CENTER | Gravity.START,
      Gravity.CENTER,
      Gravity.CENTER | Gravity.RIGHT,
      Gravity.BOTTOM | Gravity.LEFT,
      Gravity.BOTTOM | Gravity.CENTER,
      Gravity.BOTTOM | Gravity.END};

  private final static String ERROR_LINK = "https://www.error.com/link";

  private final static String WELL_LINK = "https://www.codeproject.com/KB/GDI-plus/ImageProcessing2/flip.jpg";

  private ImageView image;

  private int gravityIndex = 0;

  private String link = ERROR_LINK;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_text_activity);

    image = findViewById(R.id.image);

    reload();

    ((CheckBox) findViewById(R.id.cbUseCorrectLink)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override public void onCheckedChanged(CompoundButton compoundButton, boolean isCheck) {
        if (isCheck) {
          link = WELL_LINK;
        } else {
          link = ERROR_LINK;
        }
        reload();
      }
    });

    findViewById(R.id.bPreviousTextGravity).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        gravityIndex = gravities.length - (Math.abs(gravityIndex - 1) % gravities.length) - 1;
        reload();
      }
    });

    findViewById(R.id.bNextTextGravity).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        gravityIndex = (gravityIndex + 1) % gravities.length;
        reload();
      }
    });
  }

  private void reload() {
    Picasso.with()
        .load(link)
//        .placeholder(new TextDrawable.Builder("PL")
//            .setTextColor(Color.BLACK)
//            .setBackgroundColor(Color.BLUE)
//            .setTextGravity(gravities[gravityIndex]))
        .error(new TextDrawable.Builder("ER")
            .setTextColor(Color.GREEN)
            .setBackgroundColor(Color.GRAY)
            .setTextGravity(gravities[gravityIndex])
            .setTextFont(Typeface.DEFAULT_BOLD))
        .tag(this)
        .into(image);
  }
}
