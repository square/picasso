package com.squareup.picasso3.decoder.svg;

import android.net.Uri;
import com.squareup.picasso3.Request;
import java.io.IOException;
import java.io.InputStream;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.picasso3.ImageDecoder.Image;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class SvgImageDecoderTest {

  private SvgImageDecoder decoder;

  @Before public void setup() {
    decoder = new SvgImageDecoder();
  }

  @Test public void canHandleSource_forSvg_returnsTrue() {
    BufferedSource svg = bufferResource("/android.svg");
    assertThat(decoder.canHandleSource(svg)).isTrue();
  }

  @Test public void canHandleSource_forBitmap_returnsFalse() {
    BufferedSource jpg = bufferResource("/image.jpg");
    assertThat(decoder.canHandleSource(jpg)).isFalse();
  }

  @Test public void decodeImage_withoutTargetSize_returnsNativelySizedImage() throws IOException {
    BufferedSource svg = bufferResource("/android.svg");
    Request request = new Request.Builder(mock(Uri.class)).build();
    Image image = decoder.decodeImage(svg, request);

    assertThat(image.bitmap).isNotNull();
    assertThat(image.bitmap.getWidth()).isEqualTo(96);
    assertThat(image.bitmap.getHeight()).isEqualTo(105);
  }

  @Test public void decodeImage_withTargetSize_returnsResizedImage() throws IOException {
    BufferedSource svg = bufferResource("/android.svg");
    Request request = new Request.Builder(mock(Uri.class))
        .resize(50, 50)
        .build();
    Image image = decoder.decodeImage(svg, request);

    assertThat(image.bitmap).isNotNull();
    assertThat(image.bitmap.getWidth()).isEqualTo(50);
    assertThat(image.bitmap.getHeight()).isEqualTo(50);
  }

  private BufferedSource bufferResource(String name) {
    InputStream in = SvgImageDecoderTest.class.getResourceAsStream(name);
    if (in == null) {
      throw new IllegalArgumentException("Unknown resource for name: " + name);
    }
    return Okio.buffer(Okio.source(in));
  }

}