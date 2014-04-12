package com.squareup.picasso;

import android.graphics.Bitmap;
import android.net.Uri;

import java.io.IOException;

/** Image generator */
public interface Generator {
  /**
   * Decode source uri into a new bitmap.
   * The source uri will have a {@link com.squareup.picasso.Picasso#SCHEME_CUSTOM} scheme
   */
  Bitmap decode( Uri uri ) throws IOException;
}
