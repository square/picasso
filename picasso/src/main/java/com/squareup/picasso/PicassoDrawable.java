/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;

final class PicassoDrawable extends Drawable {
  // Only accessed from main thread.
  private static final Paint DEBUG_PAINT = new Paint();

  private static final float FADE_DURATION = 200f; //ms

  /**
   * Create or update the drawable on the target {@link ImageView} to display the supplied bitmap
   * image.
   */
  static void setBitmap(ImageView target, Context context, Bitmap bitmap,
      Picasso.LoadedFrom loadedFrom, boolean noFade, boolean debugging) {
    Drawable placeholder = target.getDrawable();
    PicassoDrawable drawable =
        new PicassoDrawable(context, placeholder, bitmap, loadedFrom, noFade, debugging);
    target.setImageDrawable(drawable);
  }

  /**
   * Create or update the drawable on the target {@link ImageView} to display the supplied
   * placeholder image.
   */
  static void setPlaceholder(ImageView target, int placeholderResId, Drawable placeholderDrawable) {
    if (placeholderResId != 0) {
      target.setImageResource(placeholderResId);
    } else {
      target.setImageDrawable(placeholderDrawable);
    }
  }

  private final boolean debugging;
  private final float density;

  final BitmapDrawable image;
  private final Picasso.LoadedFrom loadedFrom;
  Drawable placeholder;

  private long startTimeMillis;
  boolean animating;

  PicassoDrawable(Context context, Drawable placeholder, Bitmap bitmap,
      Picasso.LoadedFrom loadedFrom, boolean noFade, boolean debugging) {
    Resources res = context.getResources();

    this.debugging = debugging;
    this.density = res.getDisplayMetrics().density;

    this.loadedFrom = loadedFrom;

    this.image = new BitmapDrawable(res, bitmap);

    boolean fade = loadedFrom != MEMORY && !noFade;
    if (fade) {
      this.placeholder = placeholder;
      animating = true;
      startTimeMillis = SystemClock.uptimeMillis();
    }
  }

  @Override public void draw(Canvas canvas) {
    if (!animating) {
      image.draw(canvas);
    } else {
      if (placeholder != null) {
        placeholder.draw(canvas);
      }

      float normalized = (SystemClock.uptimeMillis() - startTimeMillis) / FADE_DURATION;
      int alpha = (int) (0xFF * normalized);

      if (normalized >= 1f) {
        animating = false;
        placeholder = null;
        image.draw(canvas);
      } else {
        image.setAlpha(alpha);
        image.draw(canvas);
        image.setAlpha(0xFF);
        invalidateSelf();
      }
    }

    if (debugging) {
      drawDebugIndicator(canvas);
    }
  }

  @Override public int getIntrinsicWidth() {
    return image.getIntrinsicWidth();
  }

  @Override public int getIntrinsicHeight() {
    return image.getIntrinsicHeight();
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

    image.setBounds(bounds);
    if (placeholder != null) {
      // Center placeholder inside the image bounds
      setBounds(placeholder);
    }
  }

  private void setBounds(Drawable drawable) {
    Rect bounds = getBounds();

    final int width = bounds.width();
    final int height = bounds.height();
    final float ratio = (float) width / height;

    final int drawableWidth = drawable.getIntrinsicWidth();
    final int drawableHeight = drawable.getIntrinsicHeight();
    final float drawableRatio = (float) drawableWidth / drawableHeight;

    if (drawableRatio < ratio) {
      final float scale = (float) height / drawableHeight;
      final int scaledDrawableWidth = (int) (drawableWidth * scale);
      final int drawableLeft = bounds.left - (scaledDrawableWidth - width) / 2;
      final int drawableRight = drawableLeft + scaledDrawableWidth;
      drawable.setBounds(drawableLeft, bounds.top, drawableRight, bounds.bottom);
    } else {
      final float scale = (float) width / drawableWidth;
      final int scaledDrawableHeight = (int) (drawableHeight * scale);
      final int drawableTop = bounds.top - (scaledDrawableHeight - height) / 2;
      final int drawableBottom = drawableTop + scaledDrawableHeight;
      drawable.setBounds(bounds.left, drawableTop, bounds.right, drawableBottom);
    }
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
