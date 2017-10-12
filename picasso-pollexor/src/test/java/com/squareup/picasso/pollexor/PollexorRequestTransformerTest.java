package com.squareup.picasso.pollexor;

import android.net.Uri;
import com.squareup.picasso.Request;
import com.squareup.pollexor.Thumbor;
import com.squareup.pollexor.ThumborUrlBuilder.ImageFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso.Picasso.RequestTransformer;
import static com.squareup.pollexor.ThumborUrlBuilder.format;
import static org.robolectric.annotation.Config.NONE;

@RunWith(RobolectricGradleTestRunner.class)
@Config(
    sdk = 17,
    manifest = NONE,
    constants = BuildConfig.class
)
public class PollexorRequestTransformerTest {
  private static final String HOST = "http://example.com/";
  private static final String KEY = "omgsecretpassword";
  private static final String IMAGE = "http://google.com/logo.png";
  private static final Uri IMAGE_URI = Uri.parse(IMAGE);

  private RequestTransformer transformer = new PollexorRequestTransformer(Thumbor.create(HOST));
  private RequestTransformer secureTransformer =
      new PollexorRequestTransformer(Thumbor.create(HOST, KEY));

  @Test public void resourceIdRequestsAreNotTransformed() {
    Request input = new Request.Builder(12).build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isSameAs(input);
  }

  @Test public void nonHttpRequestsAreNotTransformed() {
    Request input = new Request.Builder(IMAGE_URI).build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isSameAs(input);
  }

  @Test public void nonResizedRequestsAreNotTransformed() {
    Request input = new Request.Builder(IMAGE_URI).build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isSameAs(input);
  }

  @Test public void simpleResize() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();

    String expected = Thumbor.create(HOST).buildImage(IMAGE).resize(50, 50).toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Config(sdk = 18)
  @Test public void simpleResizeOnJbMr2UsesWebP() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();

    String expected = Thumbor.create(HOST)
        .buildImage(IMAGE)
        .resize(50, 50)
        .filter(format(ImageFormat.WEBP))
        .toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test public void simpleResizeWithCenterCrop() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).centerCrop().build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();
    assertThat(output.centerCrop).isFalse();

    String expected = Thumbor.create(HOST).buildImage(IMAGE).resize(50, 50).toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test public void simpleResizeWithCenterInside() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).centerInside().build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();
    assertThat(output.centerInside).isFalse();

    String expected = Thumbor.create(HOST).buildImage(IMAGE).resize(50, 50).fitIn().toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test public void simpleResizeWithEncryption() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).build();
    Request output = secureTransformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();

    String expected = Thumbor.create(HOST, KEY).buildImage(IMAGE).resize(50, 50).toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test public void simpleResizeWithCenterInsideAndEncryption() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).centerInside().build();
    Request output = secureTransformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();
    assertThat(output.centerInside).isFalse();

    String expected = Thumbor.create(HOST, KEY).buildImage(IMAGE).resize(50, 50).fitIn().toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }
}
