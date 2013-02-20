package com.squareup.picasso;

import android.graphics.Bitmap;

public interface Target {
  void onSuccess(Bitmap bitmap);
  void onError();
}
