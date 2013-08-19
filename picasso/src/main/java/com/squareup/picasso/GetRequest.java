package com.squareup.picasso;

import android.graphics.Bitmap;
import android.net.Uri;
import java.util.List;

class GetRequest extends Request<Void> {

  GetRequest(Picasso picasso, Uri uri, int resourceId, PicassoBitmapOptions bitmapOptions,
      List<Transformation> transformations, boolean skipCache, String key) {
    super(picasso, uri, resourceId, null, bitmapOptions, transformations, skipCache, false, 0, null,
        key);
  }

  @Override void complete(Bitmap result, Picasso.LoadedFrom from) {
  }

  @Override public void error() {
  }
}
