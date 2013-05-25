package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBitmap;
import org.robolectric.shadows.ShadowMatrix;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static org.fest.assertions.api.ANDROID.assertThat;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.entry;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PicassoTransformTest {
  @Test public void exifRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);

    Bitmap result = Picasso.transformResult(null, source, 90);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
  }

  @Test public void exifRotationWithManualRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;

    Bitmap result = Picasso.transformResult(options, source, 90);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("rotate 90.0");
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void rotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0"));
  }

  @Test public void pivotRotation() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetRotation = -45;
    options.targetPivotX = 10;
    options.targetPivotY = 10;
    options.hasRotationPivot = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("rotate", "-45.0 10.0 10.0"));
  }

  @Test public void scale() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetScaleX = -0.5f;
    options.targetScaleY = 2;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getSetOperations()).contains(entry("scale", "-0.5 2.0"));
  }

  @Test public void resize() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 20;
    options.targetHeight = 15;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.0 1.5");
  }

  @Test public void centerCropTallTooSmall() {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 40;
    options.targetHeight = 40;
    options.centerCrop = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(5);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 4.0 4.0");
  }

  @Test public void centerCropTallTooLarge() {
    Bitmap source = Bitmap.createBitmap(100, 200, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerCrop = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(50);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(100);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(100);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerCropWideTooSmall() {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 40;
    options.targetHeight = 40;
    options.centerCrop = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(5);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(10);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(10);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 4.0 4.0");
  }

  @Test public void centerCropWideTooLarge() {
    Bitmap source = Bitmap.createBitmap(200, 100, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerCrop = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);
    assertThat(shadowBitmap.getCreatedFromX()).isEqualTo(50);
    assertThat(shadowBitmap.getCreatedFromY()).isEqualTo(0);
    assertThat(shadowBitmap.getCreatedFromWidth()).isEqualTo(100);
    assertThat(shadowBitmap.getCreatedFromHeight()).isEqualTo(100);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerInsideTallTooSmall() {
    Bitmap source = Bitmap.createBitmap(20, 10, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideTallTooLarge() {
    Bitmap source = Bitmap.createBitmap(100, 50, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void centerInsideWideTooSmall() {
    Bitmap source = Bitmap.createBitmap(10, 20, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 2.5 2.5");
  }

  @Test public void centerInsideWideTooLarge() {
    Bitmap source = Bitmap.createBitmap(50, 100, ARGB_8888);
    PicassoBitmapOptions options = new PicassoBitmapOptions();
    options.targetWidth = 50;
    options.targetHeight = 50;
    options.centerInside = true;

    Bitmap result = Picasso.transformResult(options, source, 0);

    ShadowBitmap shadowBitmap = shadowOf(result);
    assertThat(shadowBitmap.getCreatedFromBitmap()).isSameAs(source);

    Matrix matrix = shadowBitmap.getCreatedFromMatrix();
    ShadowMatrix shadowMatrix = shadowOf(matrix);
    assertThat(shadowMatrix.getPreOperations()).containsOnly("scale 0.5 0.5");
  }

  @Test public void reusedBitmapIsNotRecycled() {
    Bitmap source = Bitmap.createBitmap(10, 10, ARGB_8888);
    Bitmap result = Picasso.transformResult(null, source, 0);
    assertThat(result).isSameAs(source).isNotRecycled();
  }
}
