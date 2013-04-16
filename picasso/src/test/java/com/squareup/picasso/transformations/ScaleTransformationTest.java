package com.squareup.picasso.transformations;

import android.graphics.Bitmap;
import com.squareup.picasso.PicassoTestRunner;
import com.squareup.picasso.Transformation;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.fest.assertions.api.ANDROID.assertThat;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(PicassoTestRunner.class)
public class ScaleTransformationTest {
  @Test public void sameScaleReturnsSource() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    ScaleTransformation transformation = new ScaleTransformation(1);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isSameAs(source);
    assertThat(actual).isNotRecycled();
  }

  @Test public void scaleDown() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    ScaleTransformation transformation = new ScaleTransformation(0.5f);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isNotSameAs(source).hasWidth(5).hasHeight(5).isNotRecycled();
    assertThat(source).isRecycled();
  }

  @Test public void scaleUp() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    ScaleTransformation transformation = new ScaleTransformation(2);
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isNotSameAs(source).hasWidth(20).hasHeight(20).isNotRecycled();
    assertThat(source).isRecycled();
  }

  @Test public void keyDependsOnFactor() {
    Transformation transformation1 =  new ScaleTransformation(2);
    Transformation transformation2 =  new ScaleTransformation(4);
    assertThat(transformation1.key()).isNotEqualTo(transformation2.key());
  }

  @Test public void keyIsNotReferenceBased() {
    Transformation transformation1 = new ScaleTransformation(2);
    Transformation transformation2 = new ScaleTransformation(2);
    assertThat(transformation1.key()).isEqualTo(transformation2.key());
  }
}
