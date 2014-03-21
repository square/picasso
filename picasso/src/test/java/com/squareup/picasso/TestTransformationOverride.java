package com.squareup.picasso;

import android.graphics.Bitmap;

/**
 * Sample Transformation Override
 * <p>
 * If the image is larger than the size provided by
 * {@link com.squareup.picasso.Request#targetWidth} or {@link com.squareup.picasso.Request#targetHeight},
 * it crops the image from the top center. Otherwise, it returns the original image.
 */
public class TestTransformationOverride extends TestTransformation implements TransformationOverride {

  TestTransformationOverride(String key, Bitmap result) {
    super(key, result);
  }

  @Override
  public Bitmap transform(Request data, Bitmap source, int exifRotation) {
    return super.transform(source);
  }

}
