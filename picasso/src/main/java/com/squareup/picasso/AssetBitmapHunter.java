package com.squareup.picasso;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class AssetBitmapHunter extends BitmapHunter {
  private AssetManager assetManager;

  public AssetBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(picasso, dispatcher, cache, stats, action);
    assetManager = context.getAssets();
  }

  @Override Bitmap decode(Request data) throws IOException {
    return decodeAsset(data);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  protected Bitmap decodeAsset(Request data) throws IOException {
    BitmapFactory.Options options = null;
    if (data.hasSize()) {
      options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      InputStream is = null;
      try {
        is = assetManager.open(data.uri.toString());
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(data.targetWidth, data.targetHeight, options);
    }
    InputStream is = assetManager.open(data.uri.toString());
    try {
      return BitmapFactory.decodeStream(is, null, options);
    } finally {
      Utils.closeQuietly(is);
    }
  }
}
