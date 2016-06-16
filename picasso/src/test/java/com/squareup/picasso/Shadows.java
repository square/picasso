package com.squareup.picasso;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Network;
import android.provider.MediaStore;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import static com.squareup.picasso.TestUtils.makeBitmap;

final class Shadows {

  @Implements(MediaStore.Video.Thumbnails.class)
  public static class ShadowVideoThumbnails {

    @Implementation
    public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
        BitmapFactory.Options options) {
      return makeBitmap();
    }
  }

  @Implements(MediaStore.Images.Thumbnails.class)
  public static class ShadowImageThumbnails {

    @Implementation
    public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
        BitmapFactory.Options options) {
      return makeBitmap(20, 20);
    }
  }

  /**
   * Here because https://github.com/robolectric/robolectric/issues/2223
   */
  @Implements(Network.class)
  public static class ShadowNetwork {
  }
}
