package com.example.picasso;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Matrix;
import android.graphics.ComposeShader;
import android.graphics.BitmapShader;
import android.graphics.PorterDuff;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class ShaderEffectsDrawable extends Drawable {

  private Bitmap bitmap;
  private Paint bitmapPaint;

  public ShaderEffectsDrawable(Bitmap bitmap) {
    this.bitmap = bitmap;

    bitmapPaint = new Paint();
    bitmapPaint.setAntiAlias(true);

    int radius = bitmap.getWidth() / 2;

    RadialGradient vignette = new RadialGradient(radius, radius, radius,
        new int[] {0, 0, 0x7f000000}, new float[] {0.0f, 0.8f, 1.0f}, Shader.TileMode.CLAMP);
    Matrix oval = new Matrix(); oval.setScale(1.0f, 0.8f); vignette.setLocalMatrix(oval);

    // We can use shaders for adding effects
    bitmapPaint.setShader(new ComposeShader(new BitmapShader(this.bitmap, Shader.TileMode.CLAMP,
        Shader.TileMode.CLAMP), vignette, PorterDuff.Mode.SRC_OVER));

    // We can add a little sepia effect to the Paint using color filters.
    ColorMatrix m1 = new ColorMatrix();
    ColorMatrix m2 = new ColorMatrix();
    m1.setSaturation(0.1f);
    m2.setScale(1f, 0.95f, 0.82f, 1.0f);
    m1.setConcat(m2, m1);
    bitmapPaint.setColorFilter(new ColorMatrixColorFilter(m1));
  }

  @Override
  public void draw(Canvas canvas) {
    final Rect bounds = getBounds();
    canvas.translate(bounds.left, bounds.top);
    final long centerX = bounds.width() / 2;
    final long centerY = bounds.height() / 2;
    final long radius = Math.min(bounds.width(), bounds.height()) / 2;

    // Image
    canvas.drawCircle(centerX, centerY, radius, bitmapPaint);
  }

  @Override
  public void setAlpha(int alpha) {
    bitmapPaint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter cf) {
    bitmapPaint.setColorFilter(cf);
  }

  @Override
  public int getOpacity() {
    Bitmap bm = bitmap;
    return (bm == null || bm.hasAlpha() || bitmapPaint.getAlpha() < 255)
        ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
  }

  @Override
  public int getIntrinsicHeight() {
    return bitmap.getHeight();
  }

  @Override
  public int getIntrinsicWidth() {
    return bitmap.getWidth();
  }

}
