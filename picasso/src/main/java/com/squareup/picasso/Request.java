package com.squareup.picasso;

import android.widget.ImageView;
import java.lang.ref.WeakReference;

public class Request {
  private final String url;
  private final WeakReference<ImageView> target;

  Request(String url, ImageView imageView) {
    this.url = url;
    this.target = new WeakReference<ImageView>(imageView);
  }

  public static class Builder {
    private final String url;

    public Builder(String url) {
      this.url = url;
    }

    public Request into(ImageView target) {
      return new Request(url, target);
    }
  }
}
