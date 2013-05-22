package com.squareup.picasso;

import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RequestBuilderTest {
  @Test public void invalidPlaceholderImage() {
    try {
      new RequestBuilder().placeholder(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().placeholder(null);
      fail("Null drawable should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().placeholder(1).placeholder(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().placeholder(new ColorDrawable(0)).placeholder(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidErrorImage() {
    try {
      new RequestBuilder().error(0);
      fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().error(null);
      fail("Null drawable should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().error(1).error(new ColorDrawable(0));
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().error(new ColorDrawable(0)).error(1);
      fail("Two placeholders should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  public void fitAndResizeMutualExclusivity() {
    try {
      new RequestBuilder().resize(10, 10).fit();
      fail("Fit cannot be called after resize.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().fit().resize(10, 10);
      fail("Resize cannot be called after fit.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test(expected = IllegalStateException.class)
  public void resizeCanOnlyBeCalledOnce() {
    new RequestBuilder().resize(10, 10).resize(5, 5);
  }

  @Test public void defaultValuesIgnored() {
    RequestBuilder b = new RequestBuilder();
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
      RequestBuilder b = new RequestBuilder(null, "", type).resize(10, 10);
      assertThat(b.options.inJustDecodeBounds).isEqualTo(type != Request.Type.NETWORK);
    }
  }

  @Test public void invalidResize() {
    try {
      new RequestBuilder().resize(-1, 10);
      fail("Negative width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().resize(10, -1);
      fail("Negative height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().resize(0, 10);
      fail("Zero width should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().resize(10, 0);
      fail("Zero height should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void invalidScale() {
    try {
      new RequestBuilder().scale(0);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().scale(0, 1);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().scale(1, 0);
      fail("Zero scale factor should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void invalidCenterCrop() {
    try {
      new RequestBuilder().centerCrop();
      fail("Center crop without resize should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullTransformationsInvalid() {
    new RequestBuilder().transform(null);
  }

  @Test public void nullTargetsInvalid() {
    try {
      new RequestBuilder().into((ImageView) null);
      fail("Null ImageView should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new RequestBuilder().into((Target) null);
      fail("Null Target should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }
}
