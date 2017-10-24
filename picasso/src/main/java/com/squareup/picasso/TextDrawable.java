package com.squareup.picasso;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.Gravity;

public class TextDrawable extends Drawable {

  private Paint mPaint;

  private String mText;

  private final int mIntrinsicWidth;

  private final int mIntrinsicHeight;

  private final int mBackgroundColor;

  private final int mTextGravity;

  private TextDrawable(String text, int textColor, int backgroundColor, Typeface font,
                       int textGravity) {
    mText = text;
    mBackgroundColor = backgroundColor;
    mTextGravity = textGravity;

    mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    mPaint.setColor(textColor);
    mPaint.setAntiAlias(true);
    mPaint.setFakeBoldText(false);
    mPaint.setStyle(Paint.Style.FILL);
    mPaint.setTypeface(font);
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

    PointF pointF = initParamByGravity(mPaint, width, height, mTextGravity);

    canvas.drawText(mText, pointF.x, pointF.y, mPaint);
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

  PointF initParamByGravity(Paint paint, int width, int height, int textGravity) {
    float x = 0;
    float y = 0;
    int gravityHorizontalPart = textGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
    int gravityVerticalPart = textGravity & Gravity.VERTICAL_GRAVITY_MASK;
    if (gravityHorizontalPart == Gravity.LEFT) {
      x = 0;
      paint.setTextAlign(Paint.Align.LEFT);
    } else if (gravityHorizontalPart == Gravity.CENTER_HORIZONTAL) {
      x = width / 2;
      paint.setTextAlign(Paint.Align.CENTER);
    } else if (gravityHorizontalPart == Gravity.RIGHT) {
      x = width;
      paint.setTextAlign(Paint.Align.RIGHT);
    }

    if (gravityVerticalPart == Gravity.TOP) {
      y = 0;
    } else if (gravityVerticalPart == Gravity.CENTER_VERTICAL) {
      y = height / 2 - ((paint.descent() + paint.ascent()) / 2);
    } else if (gravityVerticalPart ==  Gravity.BOTTOM) {
      y = height;
    }
    return new PointF(x, y);
  }

  static class Builder {

    private String mText;

    private int mTextColor = Color.WHITE;

    private int mBackgroundColor = Color.argb(0, 0, 0, 0);

    private Typeface mFont = Typeface.DEFAULT;

    private int mTextGravity = Gravity.CENTER;

    public Builder setText(String text) {
      mText = text;
      return this;
    }

    public Builder setTextColor(int textColor) {
      mTextColor = textColor;
      return this;
    }

    public Builder setBackgroundColor(int backgroundColor) {
      mBackgroundColor = backgroundColor;
      return this;
    }

    public Builder setTextFont(Typeface font) {
      mFont = font;
      return this;
    }

    public Builder setTextGravity(int textGravity) {
      mTextGravity = textGravity;
      return this;
    }

    public TextDrawable build() {
      if (mText == null) {
        throw new IllegalArgumentException("Text cannot be null");
      }
      return new TextDrawable(mText, mTextColor, mBackgroundColor, mFont, mTextGravity);
    }
  }
}