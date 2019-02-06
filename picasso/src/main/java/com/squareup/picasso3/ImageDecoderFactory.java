package com.squareup.picasso3;

import androidx.annotation.Nullable;
import java.util.List;
import okio.BufferedSource;

final class ImageDecoderFactory {

  final List<ImageDecoder> decoders;

  ImageDecoderFactory(List<ImageDecoder> decoders) {
    this.decoders = decoders;
  }

  /**
   * Returns the first {@link ImageDecoder} that can handle the supplied <code>source</code>.
   * @param source The source of the image data.
   * @return The first ImageDecoder that can decode the source, or null.
   */
  @Nullable ImageDecoder getImageDecoderForSource(BufferedSource source) {
    for (int i = 0, n = decoders.size(); i < n; i++) {
      ImageDecoder decoder = decoders.get(i);
      if (decoder.canHandleSource(source.peek())) {
        return decoder;
      }
    }
    return null;
  }
}
