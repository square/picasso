package com.squareup.picasso;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.LayoutDirection;
import android.view.Gravity;

public class TextDrawable extends Drawable {

  private Paint mPaint;

  private String mText;

  private final Typeface mFont;

  private final int mTextSize;

  private final int mIntrinsicWidth;

  private final int mIntrinsicHeight;

  private final int mBackgroundColor;

  private final int mTextGravity;

  private int mPaddingLeft = 0;

  private int mPaddingRight = 0;

  private int mPaddingTop = 0;

  private int mPaddingBottom = 0;

  private TextDrawable(String text, int textColor, int textSize, int backgroundColor, Typeface font,
                       int textGravity,
                       int paddingLeft, int paddingRight, int paddingTop, int paggingBottom) {
    mText = text;
    mBackgroundColor = backgroundColor;
    mTextGravity = textGravity;
    mTextSize = textSize;
    mFont = font;

    mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
    mPaint.setColor(textColor);
    mPaint.setStyle(Paint.Style.FILL);
    mPaint.setTypeface(mFont);
    mPaint.setTextAlign(Paint.Align.CENTER);
    mPaint.setFakeBoldText(false);

    mPaddingTop = paddingTop;
    mPaddingBottom = paggingBottom;
    mPaddingLeft = paddingLeft;
    mPaddingRight = paddingRight;

    mIntrinsicWidth = (int) (mPaint.measureText(mText, 0, mText.length()) + .5);
    mIntrinsicHeight = mPaint.getFontMetricsInt(null);
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    mPaint.setTextSize(mTextSize);
    canvas.drawColor(mBackgroundColor);

    PointF pointF = initParamByGravity(mPaint, getBounds().width(), getBounds().height(), mTextGravity);

    Rect textRect = new Rect();
    mPaint.getTextBounds(mText, 0, mText.length(), textRect);

    canvas.drawText(mText, pointF.x, pointF.y, mPaint);
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      if (isAutoMirrored()) {
        textGravity = Gravity.getAbsoluteGravity(textGravity, LayoutDirection.RTL);
      }
    }
    float x = 0;
    float y = 0;
    int gravityHorizontalPart = textGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
    int gravityVerticalPart = textGravity & Gravity.VERTICAL_GRAVITY_MASK;
    if (gravityHorizontalPart == Gravity.LEFT) {
      x = mPaddingLeft;
      paint.setTextAlign(Paint.Align.LEFT);
    } else if (gravityHorizontalPart == Gravity.CENTER_HORIZONTAL) {
      x = width / 2;
      paint.setTextAlign(Paint.Align.CENTER);
    } else if (gravityHorizontalPart == Gravity.RIGHT) {
      x = width - mPaint.measureText(mText) - mPaddingRight;
      paint.setTextAlign(Paint.Align.LEFT);
    }

    if (gravityVerticalPart == Gravity.TOP) {
      y = mPaint.getTextSize() - mPaint.descent() + mPaddingTop;
    } else if (gravityVerticalPart == Gravity.CENTER_VERTICAL) {
      y = height / 2 - ((paint.descent() + paint.ascent()) / 2);
    } else if (gravityVerticalPart == Gravity.BOTTOM) {
      y = height - (paint.descent() / 2) - mPaddingBottom;
    }
    return new PointF(x, y);
  }

  public static class Builder {

    private final static int DEFAULT_TEXT_SIZE = 5;

    private final String mText;

    private int mTextSize = DEFAULT_TEXT_SIZE;

    private int mTextColor = Color.WHITE;

    private int mBackgroundColor = Color.argb(0, 0, 0, 0);

    private int mPaddingLeft = 0;

    private int mPaddingRight = 0;

    private int mPaddingTop = 0;

    private int mPaddingBottom = 0;

    private Typeface mFont = Typeface.DEFAULT;

    private int mTextGravity = Gravity.CENTER;

    public Builder(String text) {
      if (text == null) {
        throw new IllegalArgumentException("Text cannot be null");
      }
      mText = text;
    }

    public Builder setTextSize(int textSize) {
      mTextSize = textSize;
      return this;
    }

    public Builder setPadding(int left, int right, int bottom, int top) {
      mPaddingLeft = left;
      mPaddingRight = right;
      mPaddingTop = top;
      mPaddingBottom = bottom;
      return this;
    }

    public Builder setPaddingLeft(int left) {
      mPaddingLeft = left;
      return this;
    }

    public Builder setPaddingRight(int right) {
      mPaddingRight = right;
      return this;
    }

    public Builder setPaddingTop(int top) {
      mPaddingTop = top;
      return this;
    }

    public Builder setPaddingBottom(int bottom) {
      mPaddingBottom = bottom;
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
      return new TextDrawable(mText, mTextColor, mTextSize, mBackgroundColor, mFont, mTextGravity,
          mPaddingLeft, mPaddingRight, mPaddingTop, mPaddingBottom);
    }
  }
}