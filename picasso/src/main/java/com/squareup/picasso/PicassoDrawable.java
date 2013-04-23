package com.squareup.picasso;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

import static android.graphics.Color.WHITE;

final class PicassoDrawable extends BitmapDrawable {
  // Only accessed from main thread.
  private static final Paint DEBUG_PAINT = new Paint();

  private final boolean debugging;
  private final Request.LoadedFrom loadedFrom;
  private final float density;

  public PicassoDrawable(Resources res, Bitmap bitmap, boolean debugging,
      Request.LoadedFrom loadedFrom) {
    super(res, bitmap);

    if (loadedFrom == null) {
      throw new IllegalArgumentException("Loaded from must not be null.");
    }

    this.debugging = debugging;
    this.loadedFrom = loadedFrom;
    this.density = res.getDisplayMetrics().density;
  }

  @Override public void draw(Canvas canvas) {
    super.draw(canvas);

    if (debugging) {
      canvas.save();
      canvas.rotate(45);

      // Draw a white square for the indicator border.
      DEBUG_PAINT.setColor(WHITE);
      canvas.drawRect(0, -10 * density, 7.5f * density, 10 * density, DEBUG_PAINT);

      // Draw a slightly smaller square for the indicator color.
      DEBUG_PAINT.setColor(loadedFrom.debugColor);
      canvas.drawRect(0, -9 * density, 6.5f * density, 9 * density, DEBUG_PAINT);

      canvas.restore();
    }
  }
}
