package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import java.io.IOException;
import java.io.InputStream;
import okio.BufferedSource;

import static com.squareup.picasso3.BitmapUtils.calculateInSampleSize;
import static com.squareup.picasso3.BitmapUtils.createBitmapOptions;
import static com.squareup.picasso3.BitmapUtils.requiresInSampleSize;

public final class BitmapImageDecoder implements ImageDecoder {

  @Override public boolean canHandleSource(BufferedSource source) {
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

  /**
   * Decode a byte stream into a Bitmap. This method will take into account additional information
   * about the supplied request in order to do the decoding efficiently (such as through leveraging
   * {@code inSampleSize}).
   */
  @Override public Image decodeImage(BufferedSource source, Request request) throws IOException {
    boolean isWebPFile = Utils.isWebPFile(source);
    boolean isPurgeable = request.purgeable && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    BitmapFactory.Options options = createBitmapOptions(request);
    boolean calculateSize = requiresInSampleSize(options);

    Bitmap bitmap;
    // We decode from a byte array because, a) when decoding a WebP network stream, BitmapFactory
    // throws a JNI Exception, so we workaround by decoding a byte array, or b) user requested
    // purgeable, which only affects bitmaps decoded from byte arrays.
    if (isWebPFile || isPurgeable) {
      byte[] bytes = source.readByteArray();
      if (calculateSize) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        calculateInSampleSize(request.targetWidth, request.targetHeight, options,
            request);
      }
      bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    } else {
      if (calculateSize) {
        InputStream stream = new SourceBufferingInputStream(source);
        BitmapFactory.decodeStream(stream, null, options);
        calculateInSampleSize(request.targetWidth, request.targetHeight, options,
            request);
      }
      bitmap = BitmapFactory.decodeStream(source.inputStream(), null, options);
    }
    if (bitmap == null) {
      // Treat null as an IO exception, we will eventually retry.
      throw new IOException("Failed to decode bitmap.");
    }
    return new Image(bitmap);
  }
}
