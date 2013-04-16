package com.squareup.picasso;

import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.squareup.picasso.Request.Builder;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

@RunWith(PicassoTestRunner.class)
public class RequestTest {
  @Test public void invalidPlaceholderImage() {
    try {
      new Builder().placeholder(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().placeholder(null);
      fail("Null drawable should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().placeholder(1).placeholder(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new Builder().placeholder(new ColorDrawable(0)).placeholder(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidErrorImage() {
    try {
      new Builder().error(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().error(null);
      fail("Null drawable should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().error(1).error(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new Builder().error(new ColorDrawable(0)).error(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  public void fitAndResizeMutualExclusivity() {
    try {
      new Builder().resize(10, 10).fit();
      fail("Fit cannot be called after resize.");
    } catch (IllegalStateException expected) {
    }
    try {
      new Builder().fit().resize(10, 10);
      fail("Resize cannot be called after fit.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test(expected = IllegalStateException.class)
  public void resizeCanOnlyBeCalledOnce() {
    new Builder().resize(10, 10).resize(5, 5);
  }

  @Test public void defaultValuesIgnored() {
    Builder b = new Builder();
    b.scale(1);
    assertThat(b.options).isNull();
    b.scale(1, 1);
    assertThat(b.options).isNull();
    b.rotate(0);
    assertThat(b.options).isNull();
    b.rotate(0, 40, 10);
    assertThat(b.options).isNull();
  }

  @Test public void streamDoesNotUseBoundsDecoding() {
    for (Request.Type type : Request.Type.values()) {
      Builder b = new Builder(null, "", type).resize(10, 10);
      assertThat(b.options.inJustDecodeBounds).isEqualTo(type != Request.Type.STREAM);
    }
  }

  @Test public void invalidResize() {
    try {
      new Builder().resize(-1, 10);
      fail("Negative width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().resize(10, -1);
      fail("Negative height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().resize(0, 10);
      fail("Zero width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().resize(10, 0);
      fail("Zero height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void invalidScale() {
    try {
      new Builder().scale(0);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().scale(0, 1);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().scale(1, 0);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullTransformationsInvalid() {
    new Builder().transform(null);
  }

  @Test public void nullTargetsInvalid() {
    try {
      new Builder().into((ImageView) null);
      fail("Null ImageView should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new Builder().into((Target) null);
      fail("Null Target should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }
}
