/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.graphics.Bitmap;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

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
    Picasso picasso = mock(Picasso.class);
    TargetRequest tr = new TargetRequest(picasso, URL, 0, recycler, false, null, null, false, false);
    tr.result = Bitmap.createBitmap(10, 10, null);
    try {
      tr.complete();
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
