package com.squareup.picasso;

import android.graphics.Bitmap;
import android.net.Uri;
import java.util.List;

class FetchRequest extends Request<Void> {

  FetchRequest(Picasso picasso, Uri uri, int resourceId, PicassoBitmapOptions bitmapOptions,
      List<Transformation> transformations, boolean skipCache) {
    super(picasso, uri, resourceId, null, bitmapOptions, transformations, skipCache, false, 0, null,
        null);
  }

  @Override void complete(Bitmap result, Picasso.LoadedFrom from) {
  }

  @Override public void error() {
  }
}
