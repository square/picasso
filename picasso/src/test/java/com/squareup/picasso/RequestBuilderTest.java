package com.squareup.picasso;

import android.R;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

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
    try {
      new RequestBuilder().resize(10, 10).centerInside().centerCrop();
      fail("Calling center crop after center inside should throw exception.");
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void invalidCenterInside() {
    try {
      new RequestBuilder().centerInside();
      fail("Center inside without resize should throw exception.");
    } catch (IllegalStateException expected) {
    }
    try {
      new RequestBuilder().resize(10, 10).centerInside().centerCrop();
      fail("Calling center inside after center crop should throw exception.");
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

  @Test public void noImageGetDoesNothingAndReturnsNull() throws Exception {
    final Picasso picasso = mock(Picasso.class);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Bitmap> result = new AtomicReference<Bitmap>();

    // Calling get() explodes when you call it from the main thread. Spin up a quick BG thread.
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          result.set(new RequestBuilder(picasso, null, 0).get());
        } catch (IOException e) {
          fail(e.getMessage());
        } finally {
          latch.countDown();
        }
      }
    }).start();
    latch.await();

    assertThat(result.get()).isNull();
    verifyZeroInteractions(picasso);
  }

  @Test public void noImageFetchTargetDoesNothing() {
    Picasso picasso = mock(Picasso.class);
    Target target = mock(Target.class);

    new RequestBuilder(picasso, null, 0).fetch(target);

    verify(picasso).cancelRequest(target);
    verifyZeroInteractions(target);
  }

  @Test public void noImageIntoTargetDoesNothing() {
    Picasso picasso = mock(Picasso.class);
    Target target = mock(Target.class);

    new RequestBuilder(picasso, null, 0).into(target);

    verify(picasso).cancelRequest(target);
    verifyZeroInteractions(target);
  }

  @Test public void noImageDoesNotSubmitRequest() {
    Picasso picasso = mock(Picasso.class);
    ImageView target = mock(ImageView.class);

    new RequestBuilder(picasso, null, 0).into(target);

    verify(picasso).cancelRequest(target);
    verifyZeroInteractions(target);
  }

  @Test public void noImageWithPlaceholderDoesNotSubmitAndSetsPlaceholder() {
    Picasso picasso = spy(new Picasso(Robolectric.application, null, null, null, null, null));
    ImageView target = mock(ImageView.class);

    new RequestBuilder(picasso, null, 0).placeholder(R.drawable.ic_dialog_map).into(target);

    verify(picasso).cancelRequest(target);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
  }

  @Test public void noImageWithNullPlaceholderDoesNotSubmitAndClears() {
    Picasso picasso = mock(Picasso.class);
    ImageView target = mock(ImageView.class);

    new RequestBuilder(picasso, null, 0).placeholder(null).into(target);

    verify(picasso).cancelRequest(target);
    verify(target).setImageDrawable(null);
  }
}
