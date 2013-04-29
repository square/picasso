package com.squareup.picasso;

import android.graphics.Bitmap;

/**
 * Represents an arbitrary listener for image loading.
 * <p/>
 * Objects implementing this class <strong>must</strong> have a working implementation of
 * {@link #equals(Object)} and {@link #hashCode()} for proper storage internally. Instances of this
 * interface will also be compared to determine if view recycling is occurring. It is recommended
 * that you add this interface directly on to a custom view type when using in an adapter to ensure
 * correct recycling behavior.
 */
public interface Target {
  /**
   * Callback when an image has been successfully loaded.
   * <p/>
   * <strong>Note:</strong> You must not recycle the bitmap.
   */
  void onSuccess(Bitmap bitmap);

  /** Callback indicating the image could not be successfully loaded. */
  void onError();
}
