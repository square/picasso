package com.squareup.picasso.transformations;

import android.app.Activity;
import android.graphics.Bitmap;
import android.widget.ImageView;
import com.squareup.picasso.PicassoTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static org.fest.assertions.api.ANDROID.assertThat;

@RunWith(PicassoTestRunner.class)
public class DeferredResizeTransformationTest {
  @Test public void emptyTargetReturnsSource() {
    Bitmap expected = Bitmap.createBitmap(0, 0, null);
    DeferredResizeTransformation transformation = new DeferredResizeTransformation(null);
    Bitmap actual = transformation.transform(expected);
    assertThat(actual).isSameAs(expected);
    assertThat(actual).isNotRecycled();
  }

  @Test public void unmeasuredImageViewReturnsSource() {
    Bitmap expected = Bitmap.createBitmap(0, 0, null);
    ImageView view = new ImageView(new Activity());
    DeferredResizeTransformation transformation = new DeferredResizeTransformation(view);
    Bitmap actual = transformation.transform(expected);
    assertThat(actual).isSameAs(expected);
    assertThat(actual).isNotRecycled();
  }

  @Test public void measuredImageViewReturnsScaled() {
    Bitmap source = Bitmap.createBitmap(10, 10, null);
    ImageView view = new ImageView(new Activity());
    DeferredResizeTransformation transformation = new DeferredResizeTransformation(view);
    view.measure(makeMeasureSpec(5, EXACTLY), makeMeasureSpec(5, EXACTLY));
    Bitmap actual = transformation.transform(source);
    assertThat(actual).isNotSameAs(source).hasWidth(5).hasHeight(5).isNotRecycled();
    assertThat(source).isRecycled();
  }
}
