package com.squareup.picasso;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.widget.ImageView;

import static android.graphics.Color.WHITE;
import static com.squareup.picasso.Request.LoadedFrom;
import static com.squareup.picasso.Request.LoadedFrom.MEMORY;

final class PicassoDrawable extends Drawable {
  // Only accessed from main thread.
  private static final Paint DEBUG_PAINT = new Paint();

  private static final float FADE_DURATION = 200f; //ms

  /**
   * Create or update the drawable on the target {@link ImageView} to display the supplied bitmap
   * image.
   */
  static void setBitmap(ImageView target, Context context, Bitmap bitmap, LoadedFrom loadedFrom,
      boolean noFade, boolean debugging) {
    PicassoDrawable picassoDrawable = extractPicassoDrawable(target);
    if (picassoDrawable != null) {
      picassoDrawable.setBitmap(bitmap, loadedFrom, noFade);
    } else {
      target.setImageDrawable(new PicassoDrawable(context, bitmap, loadedFrom, noFade, debugging));
    }
  }

  /**
   * Create or update the drawable on the target {@link ImageView} to display the supplied
   * placeholder image.
   */
  static void setPlaceholder(ImageView target, Context context, int placeholderResId,
      Drawable placeholderDrawable, boolean debugging) {
    PicassoDrawable picassoDrawable = extractPicassoDrawable(target);
    if (picassoDrawable != null) {
      picassoDrawable.setPlaceholder(placeholderResId, placeholderDrawable);
    } else {
      target.setImageDrawable(
          new PicassoDrawable(context, placeholderResId, placeholderDrawable, debugging));
    }
  }

  /**
   * Check for an existing instance of picasso drawable to save allocations if we need to set a
   * placeholder or were able to find the bitmap in the memory cache.
   */
  private static PicassoDrawable extractPicassoDrawable(ImageView target) {
    Drawable targetDrawable = target.getDrawable();
    if (targetDrawable instanceof PicassoDrawable) {
      return (PicassoDrawable) targetDrawable;
    }
    return null;
  }

  private final Context context;
  private final boolean debugging;
  private final float density;

  int placeholderResId;
  Drawable placeHolderDrawable;

  BitmapDrawable bitmapDrawable;
  private LoadedFrom loadedFrom;

  private int alpha;
  private long startTimeMillis;
  boolean animating;

  /**
   * Construct a drawable with the given placeholder (drawable or resource id). The actual bitmap
   * will be set later via
   * {@link #setBitmap(android.graphics.Bitmap, com.squareup.picasso.Request.LoadedFrom, boolean)}).
   * <p/>
   * This drawable may be re-used with view recycling by a call to
   * {@link #setBitmap(android.graphics.Bitmap, com.squareup.picasso.Request.LoadedFrom, boolean)}
   * or {@link #setPlaceholder(int, android.graphics.drawable.Drawable)}.
   */
  PicassoDrawable(Context context, int placeholderResId, Drawable placeholderDrawable,
      boolean debugging) {
    Resources resources = context.getResources();

    this.context = context.getApplicationContext();
    this.density = resources.getDisplayMetrics().density;

    this.placeholderResId = placeholderResId;
    if (placeholderResId != 0) {
      placeholderDrawable = resources.getDrawable(placeholderResId);
    }
    this.placeHolderDrawable = placeholderDrawable;

    this.debugging = debugging;
  }

  /**
   * Construct a drawable with the actual bitmap for immediate display.
   * <p/>
   * This drawable may be re-used with view recycling by a call to
   * {@link #setBitmap(android.graphics.Bitmap, com.squareup.picasso.Request.LoadedFrom, boolean)}
   * or  {@link #setPlaceholder(int, android.graphics.drawable.Drawable)}.
   */
  PicassoDrawable(Context context, Bitmap bitmap, LoadedFrom loadedFrom, boolean noFade,
      boolean debugging) {
    Resources resources = context.getResources();

    this.context = context.getApplicationContext();
    this.loadedFrom = loadedFrom;
    this.density = resources.getDisplayMetrics().density;

    // TODO remove. draw ourselves.
    this.bitmapDrawable = new BitmapDrawable(resources, bitmap);

    this.debugging = debugging;

    if (loadedFrom != MEMORY && !noFade) {
      startTimeMillis = 0;
      animating = true;
    }
  }

  @Override public void draw(Canvas canvas) {
    // If no bitmap has been set, quickly draw the placeholder which must be present and return.
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
        float normalized = (SystemClock.uptimeMillis() - startTimeMillis) / FADE_DURATION;
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
      invalidateSelf();
    }

    if (debugging) {
      drawDebugIndicator(canvas);
    }
  }

  @Override public int getIntrinsicWidth() {
    if (bitmapDrawable != null) {
      return bitmapDrawable.getIntrinsicWidth();
    }
    return -1;
  }

  @Override public int getIntrinsicHeight() {
    if (bitmapDrawable != null) {
      return bitmapDrawable.getIntrinsicHeight();
    }
    return -1;
  }

  @Override public void setAlpha(int alpha) {
    // No-op
  }

  @Override public void setColorFilter(ColorFilter cf) {
    // No-op
  }

  @Override public int getOpacity() {
    return PixelFormat.OPAQUE;
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

  /**
   * Reset to displaying the specified placeholder (drawable or resource id). This will be called
   * when a view is recycled to avoid creating a new drawable.
   */
  void setPlaceholder(int placeholderResId, Drawable placeHolderDrawable) {
    bitmapDrawable = null;
    loadedFrom = null;

    if (placeholderResId != 0) {
      if (this.placeholderResId != placeholderResId) {
        this.placeHolderDrawable = context.getResources().getDrawable(placeholderResId);
        this.placeHolderDrawable.setBounds(getBounds());
      }
    } else if (this.placeHolderDrawable != placeHolderDrawable) {
      this.placeHolderDrawable = placeHolderDrawable;
      this.placeHolderDrawable.setBounds(getBounds());
    }

    invalidateSelf();
  }

  /**
   * Set the actual bitmap that we should be displaying. If we already have an image and the source
   * of the new image was not the memory cache then perform a cross-fade.
   */
  void setBitmap(Bitmap bitmap, LoadedFrom loadedFrom, boolean noFade) {
    boolean fade = loadedFrom != MEMORY && !noFade;
    if (bitmapDrawable != null && fade) {
      placeHolderDrawable = bitmapDrawable;
    }

    bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
    bitmapDrawable.setBounds(getBounds());

    this.loadedFrom = loadedFrom;

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
