package com.squareup.picasso3;

import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import java.io.IOException;
import java.io.InputStream;
import okio.BufferedSource;

import static com.squareup.picasso3.BitmapUtils.decodeStream;

public final class BitmapImageDecoder implements ImageDecoder {

  @Override public boolean canHandleSource(@NonNull BufferedSource source) {
    try {
      if (Utils.isWebPFile(source)) {
        return true;
      }

      InputStream stream = new SourceBufferingInputStream(source);
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(stream, null, options);
      // we successfully decoded the bounds
      return options.outWidth > 0 && options.outHeight > 0;
    } catch (IOException e) {
      return false;
    }
  }

  @NonNull @Override
  public Image decodeImage(@NonNull BufferedSource source, @NonNull Request request)
      throws IOException {
    return new Image(decodeStream(source, request));
  }
}
