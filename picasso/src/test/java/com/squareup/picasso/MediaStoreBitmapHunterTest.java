package com.squareup.picasso;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.MediaStoreBitmapHunter.PicassoKind.FULL;
import static com.squareup.picasso.MediaStoreBitmapHunter.PicassoKind.MICRO;
import static com.squareup.picasso.MediaStoreBitmapHunter.PicassoKind.MINI;
import static com.squareup.picasso.MediaStoreBitmapHunter.getPicassoKind;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MediaStoreBitmapHunterTest {

  @Test public void getPicassoKindMicro() throws Exception {
    assertThat(getPicassoKind(96, 96)).isEqualTo(MICRO);
    assertThat(getPicassoKind(95, 95)).isEqualTo(MICRO);
  }

  @Test public void getPicassoKindMini() throws Exception {
    assertThat(getPicassoKind(512, 384)).isEqualTo(MINI);
    assertThat(getPicassoKind(100, 100)).isEqualTo(MINI);
  }

  @Test public void getPicassoKindFull() throws Exception {
    assertThat(getPicassoKind(513, 385)).isEqualTo(FULL);
    assertThat(getPicassoKind(1000, 1000)).isEqualTo(FULL);
    assertThat(getPicassoKind(1000, 384)).isEqualTo(FULL);
    assertThat(getPicassoKind(1000, 96)).isEqualTo(FULL);
    assertThat(getPicassoKind(96, 1000)).isEqualTo(FULL);
  }
}
