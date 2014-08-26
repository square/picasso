package com.squareup.picasso;

import android.graphics.Point;

/** Flexible image resizer. */
public interface Resizer {
  /**
   * Dynamically pick a size to scale the new bitmap to.  This function is called after an image's
   * inherent bounds are known but before the image is decoded.  That makes it a great time to
   * pick a target bitmap size that will fit on the screen or in memory.
   *
   * Here is a specific example implementation that makes an image fit within a specific width but
   * chooses a height automatically to preserve aspect ratio.
   *
   *   public Point resize(int width, int height) {
   *     if (width <= MAX_IMAGE_WIDTH) return null;
   *     float scaleRatio = MAX_IMAGE_WIDTH / (float)width;
   *     int newWidth = (int)(width * scaleRatio);
   *     int newHeight = (int)(height * scaleRatio);
   *     return new Point(newWidth, newHeight);
   *   }
   *
   * {@code width} and {@code height} are the image's inherent size.
   * Return a point containing the size you want the image scaled to.
   * Return null to indicate that {@code width} x {@code height} is fine after all.
   */
  public Point resize(int width, int height);
}
