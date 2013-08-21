package com.squareup.picasso.pollexor;

import android.net.Uri;
import com.squareup.picasso.Request;
import com.squareup.pollexor.Pollexor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Picasso.RequestTransformer;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.robolectric.annotation.Config.NONE;

@RunWith(RobolectricTestRunner.class) //
@Config(manifest = NONE)
public class PollexorRequestTransformerTest {
  private static final String HOST = "http://example.com/";
  private static final String KEY = "omgsecretpassword";
  private static final String IMAGE = "http://google.com/logo.png";
  private static final Uri IMAGE_URI = Uri.parse(IMAGE);

  private RequestTransformer transformer = new PollexorRequestTransformer(HOST);
  private RequestTransformer secureTransformer = new PollexorRequestTransformer(HOST, KEY);

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

  @Test public void centerCropRequestsAreNotTransformed() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).centerCrop().build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isSameAs(input);
  }

  @Test public void simpleResize() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();

    String expected = Pollexor.image(IMAGE).host(HOST).resize(50, 50).toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test public void simpleResizeWithCenterInside() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).centerInside().build();
    Request output = transformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();
    assertThat(output.centerInside).isFalse();

    String expected = Pollexor.image(IMAGE).host(HOST).resize(50, 50).fitIn().toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test public void simpleResizeWithEncryption() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).build();
    Request output = secureTransformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();

    String expected = Pollexor.image(IMAGE).host(HOST).key(KEY).resize(50, 50).toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test public void simpleResizeWithCenterInsideAndEncryption() {
    Request input = new Request.Builder(IMAGE_URI).resize(50, 50).centerInside().build();
    Request output = secureTransformer.transformRequest(input);
    assertThat(output).isNotSameAs(input);
    assertThat(output.hasSize()).isFalse();
    assertThat(output.centerInside).isFalse();

    String expected = Pollexor.image(IMAGE).host(HOST).key(KEY).resize(50, 50).fitIn().toUrl();
    assertThat(output.uri.toString()).isEqualTo(expected);
  }
}
