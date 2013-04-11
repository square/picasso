package com.squareup.picasso.sample;

import android.content.Context;
import android.widget.ImageView;

/** An image view which always remains square with respect to its width. */
public class SquaredImageView extends ImageView {
  public SquaredImageView(Context context) {
    super(context);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
  }
}
