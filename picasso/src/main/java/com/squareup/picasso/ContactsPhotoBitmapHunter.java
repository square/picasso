package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Request.LoadedFrom.DISK;
import static com.squareup.picasso.Utils.calculateInSampleSize;

class ContactsPhotoBitmapHunter extends StreamBitmapHunter {

  final Context context;

  ContactsPhotoBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher,
      Request request) {
    super(picasso, dispatcher, request);
    this.context = context;
  }

  @Override InputStream getInputStream() throws IOException {
    return Utils.getContactPhotoStream(context.getContentResolver(), uri);
  }

  @Override Bitmap decodeStream(InputStream stream, PicassoBitmapOptions options)
      throws IOException {
    if (options != null && options.inJustDecodeBounds) {
      InputStream is = null;
      try {
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      calculateInSampleSize(options);
    }
    return BitmapFactory.decodeStream(stream, null, options);
  }

  @Override Request.LoadedFrom getLoadedFrom() {
    return DISK;
  }
}
