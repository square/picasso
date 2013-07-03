package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Request.LoadedFrom;
import static com.squareup.picasso.Request.LoadedFrom.DISK;
import static com.squareup.picasso.Request.LoadedFrom.NETWORK;
import static com.squareup.picasso.Utils.calculateInSampleSize;

class NetworkBitmapHunter extends StreamBitmapHunter {
  private final Downloader downloader;
  private LoadedFrom loadedFrom;

  public NetworkBitmapHunter(Picasso picasso, Dispatcher dispatcher, Request request,
      Downloader downloader) {
    super(picasso, dispatcher, request);
    this.downloader = downloader;
  }

  @Override InputStream getInputStream() throws IOException {
    Response response = downloader.load(uri, false);
    loadedFrom = response.cached ? DISK : NETWORK;
    return response.stream;
  }

  @Override Bitmap decodeStream(InputStream stream, PicassoBitmapOptions options)
      throws IOException {
    if (stream == null) {
      return null;
    }
    try {
      if (options != null && options.inJustDecodeBounds) {
        MarkableInputStream markStream = new MarkableInputStream(stream);
        stream = markStream;

        long mark = markStream.savePosition(1024); // Mirrors BitmapFactory.cpp value.
        BitmapFactory.decodeStream(stream, null, options);
        calculateInSampleSize(options);

        markStream.reset(mark);
      }
      return BitmapFactory.decodeStream(stream, null, options);
    } finally {
      Utils.closeQuietly(stream);
    }
  }

  @Override LoadedFrom getLoadedFrom() {
    return loadedFrom;
  }
}
