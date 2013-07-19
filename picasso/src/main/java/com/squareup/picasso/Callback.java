package com.squareup.picasso;

public interface Callback {
  void onImageLoaded();

  void onImageFailed();

  public static class EmptyCallback implements Callback {

    @Override public void onImageLoaded() {
    }

    @Override public void onImageFailed() {
    }
  }
}
