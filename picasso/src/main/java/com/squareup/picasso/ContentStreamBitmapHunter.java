package com.squareup.picasso;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Request.LoadedFrom.DISK;
import static com.squareup.picasso.Utils.calculateInSampleSize;

class ContentStreamBitmapHunter extends BitmapHunter {

  final Context context;

  ContentStreamBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher,
      Request request) {
    super(picasso, dispatcher, request);
    this.context = context;
  }

  @Override Bitmap decode(Uri uri, PicassoBitmapOptions options) throws IOException {
    return decodeContentStream(uri, options);
  }

  @Override Request.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  Bitmap decodeContentStream(Uri path, PicassoBitmapOptions options) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    if (options != null && options.inJustDecodeBounds) {
      InputStream is = null;
      try {
        is = contentResolver.openInputStream(path);
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(options);
    }
    InputStream is = null;
    try {
      is = contentResolver.openInputStream(path);
      return BitmapFactory.decodeStream(contentResolver.openInputStream(path), null, options);
    } finally {
      Utils.closeQuietly(is);
    }
  }
}
