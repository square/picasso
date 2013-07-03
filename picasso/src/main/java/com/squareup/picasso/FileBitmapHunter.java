package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.IOException;

class FileBitmapHunter extends ContentStreamBitmapHunter {

  FileBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Request request) {
    super(context, picasso, dispatcher, request);
  }

  @Override Bitmap decode(Uri uri, PicassoBitmapOptions options) throws IOException {
    options.exifRotation = Utils.getFileExifRotation(uri.getPath());
    return super.decode(uri, options);
  }
}
