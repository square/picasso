package com.squareup.picasso.transformations;

import android.graphics.Bitmap;
import com.squareup.picasso.PicassoTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.fest.assertions.api.ANDROID.assertThat;

@RunWith(PicassoTestRunner.class)
public class RotateTransformationTest {
  @Test public void noRotationReturnsSource() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    RotationTransformation transformation = new RotationTransformation(0);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isSameAs(source);
    assertThat(actual).isNotRecycled();
  }

  @Test public void noRotationDifferentPivotReturnsSource() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    RotationTransformation transformation = new RotationTransformation(0, 4, 4);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isSameAs(source);
    assertThat(actual).isNotRecycled();
  }

  @Test public void rotateWithoutPivot() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    RotationTransformation transformation = new RotationTransformation(90);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isNotSameAs(source).hasWidth(10).hasHeight(10).isNotRecycled();
    assertThat(source).isRecycled();
  }

  @Test public void rotateWithPivot() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    RotationTransformation transformation = new RotationTransformation(90, 4, 4);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isNotSameAs(source).hasWidth(10).hasHeight(10).isNotRecycled();
    assertThat(source).isRecycled();
  }

  @Test public void abitraryRotationMaintainsBounds() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    RotationTransformation transformation = new RotationTransformation(43, 4, 4);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isNotSameAs(source).hasWidth(10).hasHeight(10).isNotRecycled();
    assertThat(source).isRecycled();
  }
}
