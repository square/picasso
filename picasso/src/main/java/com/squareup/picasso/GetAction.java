package com.squareup.picasso;

import android.graphics.Bitmap;

class GetAction extends Action<Void> {
  GetAction(Picasso picasso, Request data, boolean skipCache) {
    super(picasso, null, data, skipCache, false, 0, null, null);
  }

  @Override void complete(Bitmap result, Picasso.LoadedFrom from) {
  }

  @Override public void error() {
  }
}
