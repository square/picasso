package com.squareup.picasso3;

import android.support.annotation.Nullable;
import java.util.List;
import okio.BufferedSource;

final class ImageDecoderFactory {

  private final List<ImageDecoder> decoders;

  ImageDecoderFactory(List<ImageDecoder> decoders) {
    this.decoders = decoders;
  }

  /**
   * Returns the first {@link ImageDecoder} that can handle the supplied <code>source</code>.
   * @param source The source of the image data.
   * @return The first ImageDecoder that can decode the source, or null.
   */
  @Nullable ImageDecoder getImageDecoderForSource(BufferedSource source) {
    for (ImageDecoder decoder : decoders) {
      if (decoder.canHandleSource(source)) {
        return decoder;
      }
    }
    return null;
  }
}
