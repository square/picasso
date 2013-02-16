package com.squareup.picasso;

import java.io.InputStream;

public interface Downloader {
  InputStream download(String url);
}
