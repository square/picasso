package com.squareup.picasso;

public class Picasso {
  public static Request.Builder load(String url) {
    return new Request.Builder(url);
  }
}
