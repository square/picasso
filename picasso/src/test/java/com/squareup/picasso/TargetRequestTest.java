package com.squareup.picasso;

import android.graphics.Bitmap;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TargetRequestTest {
  private static final Uri URL = Uri.parse("http://example.com/1.png");

  @Test public void recyclingInSuccessThrowsException() {
    Target recycler = new Target() {
      @Override public void onSuccess(Bitmap bitmap) {
        bitmap.recycle();
      }

      @Override public void onError() {
        throw new AssertionError();
      }
    };
    TargetRequest tr = new TargetRequest(null, URL, 0, recycler, false, null, null, false);
    tr.result = Bitmap.createBitmap(10, 10, null);
    try {
      tr.complete();
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
