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
package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.widget.ImageView;
import androidx.annotation.Nullable;

import static android.graphics.Color.WHITE;
import static com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;

final class PicassoDrawable extends BitmapDrawable {
  // Only accessed from main thread.
  private static final Paint DEBUG_PAINT = new Paint();
  private static final float FADE_DURATION = 200f; //ms
  private static final int INDICATORS_WIDTH = 16;

  /**
   * Create or update the drawable on the target {@link ImageView} to display the supplied bitmap
   * image.
   */
  static void setResult(ImageView target, Context context, RequestHandler.Result result,
      boolean noFade, boolean debugging, boolean indicatorsCentered) {
    Drawable placeholder = target.getDrawable();
    if (placeholder instanceof Animatable) {
      ((Animatable) placeholder).stop();
    }

    Bitmap bitmap = result.getBitmap();
    if (bitmap != null) {
      Picasso.LoadedFrom loadedFrom = result.getLoadedFrom();
      PicassoDrawable drawable =
          new PicassoDrawable(context, bitmap, placeholder, loadedFrom, noFade, debugging,
              indicatorsCentered);
      target.setImageDrawable(drawable);
      return;
    }

    Drawable drawable = result.getDrawable();
    if (drawable != null) {
      target.setImageDrawable(drawable);
      if (drawable instanceof Animatable) {
        ((Animatable) drawable).start();
      }
    }
  }

  /**
   * Create or update the drawable on the target {@link ImageView} to display the supplied
   * placeholder image.
   */
  static void setPlaceholder(ImageView target, @Nullable Drawable placeholderDrawable) {
    target.setImageDrawable(placeholderDrawable);
    if (target.getDrawable() instanceof Animatable) {
      ((Animatable) target.getDrawable()).start();
    }
  }

  private final boolean debugging;
  private final boolean indicatorsCentered;
  private final float density;
  private final Picasso.LoadedFrom loadedFrom;

  @Nullable Drawable placeholder;

  long startTimeMillis;
  boolean animating;
  int alpha = 0xFF;

  PicassoDrawable(Context context, Bitmap bitmap, @Nullable Drawable placeholder,
      Picasso.LoadedFrom loadedFrom, boolean noFade, boolean debugging,
      boolean indicatorsCentered) {
    super(context.getResources(), bitmap);

    this.debugging = debugging;
    this.indicatorsCentered = indicatorsCentered;
    this.density = context.getResources().getDisplayMetrics().density;

    this.loadedFrom = loadedFrom;

    boolean fade = loadedFrom != MEMORY && !noFade;
    if (fade) {
      this.placeholder = placeholder;
      animating = true;
      startTimeMillis = SystemClock.uptimeMillis();
    }
  }

  @Override public void draw(Canvas canvas) {
    if (!animating) {
      super.draw(canvas);
    } else {
      float normalized = (SystemClock.uptimeMillis() - startTimeMillis) / FADE_DURATION;
      if (normalized >= 1f) {
        animating = false;
        placeholder = null;
        super.draw(canvas);
      } else {
        if (placeholder != null) {
          placeholder.draw(canvas);
        }

        // setAlpha will call invalidateSelf and drive the animation.
        int partialAlpha = (int) (alpha * normalized);
        super.setAlpha(partialAlpha);
        super.draw(canvas);
        super.setAlpha(alpha);
      }
    }

    if (debugging) {
      if (indicatorsCentered) {
        drawCenteredDebugIndicator(canvas);
      } else {
        drawDebugIndicator(canvas);
      }
    }
  }

  @Override public void setAlpha(int alpha) {
    this.alpha = alpha;
    if (placeholder != null) {
      placeholder.setAlpha(alpha);
    }
    super.setAlpha(alpha);
  }

  @Override public void setColorFilter(ColorFilter cf) {
    if (placeholder != null) {
      placeholder.setColorFilter(cf);
    }
    super.setColorFilter(cf);
  }

  @Override protected void onBoundsChange(Rect bounds) {
    if (placeholder != null) {
      placeholder.setBounds(bounds);
    }
    super.onBoundsChange(bounds);
  }

  private void drawDebugIndicator(Canvas canvas) {
    DEBUG_PAINT.setColor(WHITE);
    Path path = getTrianglePath(0, 0, (int) (INDICATORS_WIDTH * density));
    canvas.drawPath(path, DEBUG_PAINT);

    DEBUG_PAINT.setColor(loadedFrom.debugColor);
    path = getTrianglePath(0, 0, (int) ((INDICATORS_WIDTH - 1) * density));
    canvas.drawPath(path, DEBUG_PAINT);
  }

  private static Path getTrianglePath(int x1, int y1, int width) {
    final Path path = new Path();
    path.moveTo(x1, y1);
    path.lineTo(x1 + width, y1);
    path.lineTo(x1, y1 + width);

    return path;
  }

  private void drawCenteredDebugIndicator(Canvas canvas) {
    DEBUG_PAINT.setColor(WHITE);
    Path path = getCirclePath(canvas.getWidth() / 2f, canvas.getHeight() / 2f,
        (int) (INDICATORS_WIDTH * density));
    canvas.drawPath(path, DEBUG_PAINT);

    DEBUG_PAINT.setColor(loadedFrom.debugColor);
    path = getCirclePath(canvas.getWidth() / 2f, canvas.getHeight() / 2f,
        (int) ((INDICATORS_WIDTH - 1) * density));
    canvas.drawPath(path, DEBUG_PAINT);
  }

  private static Path getCirclePath(float x, float y, float radius) {
    final Path path = new Path();
    path.addCircle(x, y, radius, Path.Direction.CW);

    return path;
  }
}
