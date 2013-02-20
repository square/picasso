package com.squareup.picasso.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.squareup.picasso.Target;

/** Demonstrates the use of a {@link Target} view other than an {@link ImageView}. */
public class SampleTargetView extends LinearLayout implements Target {
  private ImageView imageView;

  public SampleTargetView(Context context) {
    super(context);
    inflate(context, R.layout.list_item, this);
    imageView = (ImageView) findViewById(R.id.image);
  }

  @Override public void onSuccess(Bitmap bitmap) {
    imageView.setImageBitmap(bitmap);
  }

  @Override public void onError() {
  }

  public void setPlaceHolder(int placeHolder) {
    imageView.setImageResource(placeHolder);
  }
}
