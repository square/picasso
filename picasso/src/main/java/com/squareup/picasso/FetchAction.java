package com.squareup.picasso;

import android.graphics.Bitmap;

class FetchAction extends Action<Void> {
  FetchAction(Picasso picasso, Request data, boolean skipCache, String key) {
    super(picasso, null, data, skipCache, false, 0, null, key);
  }

  @Override void complete(Bitmap result, Picasso.LoadedFrom from) {
  }

  @Override public void error() {
  }
}
