package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.io.IOException;
import okio.BufferedSource;

public interface ImageDecoder {

  final class Image {
    @Nullable public final Bitmap bitmap;
    @Nullable public final Drawable drawable;
    public final int exifOrientation;

    public Image(@NonNull Bitmap bitmap) {
      this(bitmap, null, 0);
    }

    public Image(@NonNull Drawable drawable) {
      this(null, drawable, 0);
    }

    public Image(@Nullable Bitmap bitmap, @Nullable Drawable drawable, int exifOrientation) {
      this.bitmap = bitmap;
      this.drawable = drawable;
      this.exifOrientation = exifOrientation;
    }
  }

  boolean canHandleSource(BufferedSource source);
  Image decodeImage(BufferedSource source, Request request) throws IOException;
}
