package com.squareup.picasso;

import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;

@RunWith(PicassoTestRunner.class)
public class TargetRequestTest {
  @Test public void recyclingInSuccessThrowsException() {
    Target recycler = new Target() {
      @Override public void onSuccess(Bitmap bitmap) {
        bitmap.recycle();
      }

      @Override public void onError() {
        throw new AssertionError();
      }
    };
    TargetRequest tr =
        new TargetRequest(null, null, 0, recycler, false, null, null, null, 0, false, null);
    tr.result = Bitmap.createBitmap(10, 10, null);
    try {
      tr.complete();
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
