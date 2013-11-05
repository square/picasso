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
import android.graphics.drawable.Drawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.URI_KEY_1;
import static com.squareup.picasso.TestUtils.mockTarget;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TargetActionTest {

  @Test(expected = AssertionError.class)
  public void throwsErrorWithNullResult() throws Exception {
    TargetAction request =
        new TargetAction(mock(Picasso.class), mockTarget(), null, false, URI_KEY_1);
    request.complete(null, MEMORY);
  }

  @Test
  public void invokesSuccessIfTargetIsNotNull() throws Exception {
    Target target = mockTarget();
    TargetAction request = new TargetAction(mock(Picasso.class), target, null, false, URI_KEY_1);
    request.complete(BITMAP_1, MEMORY);
    verify(target).onBitmapLoaded(BITMAP_1, MEMORY);
  }

  @Test
  public void invokesErrorIfTargetIsNotNull() throws Exception {
    Target target = mockTarget();
    TargetAction request = new TargetAction(mock(Picasso.class), target, null, false, URI_KEY_1);
    request.error();
    verify(target).onBitmapFailed(null);
  }

  @Test public void recyclingInSuccessThrowsException() {
    Target bad = new Target() {
      @Override public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        bitmap.recycle();
      }

      @Override public void onBitmapFailed(Drawable errorDrawable) {
        throw new AssertionError();
      }

      @Override public void onPrepareLoad(Drawable placeHolderDrawable) {
        throw new AssertionError();
      }
    };
    Picasso picasso = mock(Picasso.class);

    TargetAction tr = new TargetAction(picasso, bad, null, false, URI_KEY_1);
    try {
      tr.complete(BITMAP_1, MEMORY);
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
