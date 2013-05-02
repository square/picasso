package com.squareup.picasso;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import org.jetbrains.annotations.TestOnly;

import static android.graphics.Color.WHITE;
import static com.squareup.picasso.Request.LoadedFrom.MEMORY;

final class PicassoDrawable extends Drawable {
  // Only accessed from main thread.
  private static final Paint DEBUG_PAINT = new Paint();

  private static final int FADE_DURATION = 180;

  private final Context context;
  private final boolean debugging;
  private final float density;
  private final Request.LoadedFrom loadedFrom;

  private BitmapDrawable bitmapDrawable;
  private Drawable placeHolderDrawable;
  private int alpha;
  private long startTimeMillis;
  private boolean animating;

  public PicassoDrawable(Context context, int placeholderResId, boolean debugging) {
    this(context, context.getResources().getDrawable(placeholderResId), debugging);
  }

  public PicassoDrawable(Context context, Drawable placeholderDrawable, boolean debugging) {
    this.context = context;
    this.debugging = debugging;
    this.density = context.getResources().getDisplayMetrics().density;
    this.loadedFrom = MEMORY;
    this.placeHolderDrawable = placeholderDrawable;

    animating = false;
    startTimeMillis = 0;
  }

  public PicassoDrawable(Context context, Bitmap bitmap, boolean debugging,
      Request.LoadedFrom loadedFrom) {
    if (loadedFrom == null) {
      throw new IllegalArgumentException("Loaded from must not be null.");
    }

    Resources resources = context.getResources();

    this.context = context.getApplicationContext();
    this.debugging = debugging;
    this.loadedFrom = loadedFrom;
    this.density = resources.getDisplayMetrics().density;
    this.bitmapDrawable = new BitmapDrawable(resources, bitmap);

    if (loadedFrom != MEMORY) {
      startTimeMillis = 0;
      animating = true;
    }
  }

  @Override public void draw(Canvas canvas) {
    if (bitmapDrawable == null) {
      placeHolderDrawable.draw(canvas);
      return;
    }

    boolean done = true;

    if (animating) {
      if (startTimeMillis == 0) {
        startTimeMillis = SystemClock.uptimeMillis();
        done = false;
        alpha = 0;
      } else {
        float normalized = (float) (SystemClock.uptimeMillis() - startTimeMillis) / FADE_DURATION;
        done = normalized >= 1.0f;
        normalized = Math.min(normalized, 1.0f);
        alpha = (int) (0xFF * normalized);
      }
    }

    if (done) {
      bitmapDrawable.draw(canvas);
    } else {
      if (placeHolderDrawable != null) {
        placeHolderDrawable.draw(canvas);
      }
      if (alpha > 0) {
        bitmapDrawable.setAlpha(alpha);
        bitmapDrawable.draw(canvas);
        bitmapDrawable.setAlpha(0xFF);
      }
    }

    if (debugging) {
      drawDebugIndicator(canvas);
    }

    if (!done) {
      invalidateSelf();
    }
  }

  @Override public void setAlpha(int alpha) {
    bitmapDrawable.setAlpha(alpha);
  }

  @Override public void setColorFilter(ColorFilter cf) {
    bitmapDrawable.setColorFilter(cf);
  }

  @Override public int getOpacity() {
    return bitmapDrawable.getOpacity();
  }

  @Override protected void onBoundsChange(Rect bounds) {
    super.onBoundsChange(bounds);
    if (bitmapDrawable != null) {
      bitmapDrawable.setBounds(bounds);
    }

    if (placeHolderDrawable != null) {
      placeHolderDrawable.setBounds(bounds);
    }
  }

  @TestOnly Bitmap getBitmap() {
    return bitmapDrawable.getBitmap();
  }

  public void setBitmap(Bitmap bitmap, boolean fade) {
    if (bitmapDrawable != null) {
      placeHolderDrawable = bitmapDrawable;
      fade = true;
    }
    bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
    bitmapDrawable.setBounds(getBounds());

    startTimeMillis = 0;
    animating = fade;

    invalidateSelf();
  }

  private void drawDebugIndicator(Canvas canvas) {
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
