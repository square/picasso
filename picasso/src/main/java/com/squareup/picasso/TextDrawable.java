package com.squareup.picasso;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class TextDrawable extends Drawable {
  private static final int DEFAULT_COLOR = Color.WHITE;

  private Paint mPaint;

  private String mText;

  private final int mIntrinsicWidth;

  private final int mIntrinsicHeight;

  private final int mBackgroundColor;

  public TextDrawable(@NonNull String text, @Nullable Integer backgroundColor,
                      @Nullable Integer textColor) {
    //noinspection ConstantConditions
    if (text == null) {
      throw new IllegalArgumentException("Text cannot be null");
    }
    mText = text;
    mBackgroundColor = (backgroundColor != null) ? backgroundColor : Color.argb(0, 0, 0, 0);
    mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    mPaint.setColor((textColor != null) ? textColor : DEFAULT_COLOR);
    mPaint.setTextAlign(Align.CENTER);
    mPaint.setAntiAlias(true);
    mPaint.setFakeBoldText(false);
    mPaint.setStyle(Paint.Style.FILL);
    mPaint.setTextAlign(Paint.Align.CENTER);
    mIntrinsicWidth = (int) (mPaint.measureText(mText, 0, mText.length()) + .5);
    mIntrinsicHeight = mPaint.getFontMetricsInt(null);
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    Rect bounds = getBounds();

    int count = canvas.save();
    canvas.translate(bounds.left, bounds.top);

    // draw text
    int width = bounds.width();
    int height = bounds.height();
    int fontSize = Math.min(width, height) / 2;
    mPaint.setTextSize(fontSize);
    canvas.drawColor(mBackgroundColor);
    canvas.drawText(mText, width / 2,
            height / 2 - ((mPaint.descent() + mPaint.ascent()) / 2), mPaint);

    canvas.restoreToCount(count);
  }

  @Override
  public int getOpacity() {
    return mPaint.getAlpha();
  }

  @Override
  public int getIntrinsicWidth() {
    return mIntrinsicWidth;
  }

  @Override
  public int getIntrinsicHeight() {
    return mIntrinsicHeight;
  }

  @Override
  public void setAlpha(int alpha) {
    mPaint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    mPaint.setColorFilter(filter);
  }
}