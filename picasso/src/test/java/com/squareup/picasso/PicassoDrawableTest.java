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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Color.RED;
import static com.squareup.picasso.Request.LoadedFrom.DISK;
import static com.squareup.picasso.Request.LoadedFrom.MEMORY;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class) @Config(manifest = Config.NONE)
public class PicassoDrawableTest {
  private final Context context = Robolectric.application;
  private final Bitmap bitmap1 = Bitmap.createBitmap(10, 10, ARGB_8888);
  private final Bitmap bitmap2 = Bitmap.createBitmap(10, 10, ARGB_8888);
  private final Drawable placholder = new ColorDrawable(RED);

  @Test public void createWithPlaceholderDoesNotAnimate() {
    PicassoDrawable pd = new PicassoDrawable(context, 0, placholder, false);
    assertThat(pd.bitmapDrawable).isNull();
    assertThat(pd.placeHolderDrawable).isSameAs(placholder);
    assertThat(pd.animating).isFalse();
  }

  @Test public void createWithBitmapCacheHit() {
    PicassoDrawable pd = new PicassoDrawable(context, bitmap1, MEMORY, false, false);
    assertThat(pd.bitmapDrawable.getBitmap()).isSameAs(bitmap1);
    assertThat(pd.placeHolderDrawable).isNull();
    assertThat(pd.animating).isFalse();
  }

  @Test public void withPlaceholderToBitmap() {
    PicassoDrawable pd = new PicassoDrawable(context, 0, placholder, false);
    assertThat(pd.placeHolderDrawable).isSameAs(placholder);
    assertThat(pd.bitmapDrawable).isNull();
    assertThat(pd.animating).isFalse();

    pd.setBitmap(bitmap1, DISK, false);
    assertThat(pd.bitmapDrawable.getBitmap()).isSameAs(bitmap1);
    assertThat(pd.animating).isTrue();
  }

  @Test public void withPlaceholderToBitmapNoFade() {
    PicassoDrawable pd = new PicassoDrawable(context, 0, placholder, false);
    assertThat(pd.placeHolderDrawable).isSameAs(placholder);
    assertThat(pd.bitmapDrawable).isNull();
    assertThat(pd.animating).isFalse();

    pd.setBitmap(bitmap1, DISK, true);
    assertThat(pd.bitmapDrawable.getBitmap()).isSameAs(bitmap1);
    assertThat(pd.animating).isFalse();
  }

  @Test public void withBitmapRecycleToPlaceholder() {
    PicassoDrawable pd = new PicassoDrawable(context, bitmap1, MEMORY, false, false);
    assertThat(pd.bitmapDrawable.getBitmap()).isSameAs(bitmap1);
    assertThat(pd.placeHolderDrawable).isNull();
    assertThat(pd.animating).isFalse();

    pd.setPlaceholder(0, placholder);
    assertThat(pd.bitmapDrawable).isNull();
    assertThat(pd.placeHolderDrawable).isSameAs(placholder);
    assertThat(pd.animating).isFalse();
  }

  @Test public void withBitmapRecycleToBitmapCacheHit() {
    PicassoDrawable pd = new PicassoDrawable(context, bitmap1, MEMORY, false, false);
    assertThat(pd.bitmapDrawable.getBitmap()).isSameAs(bitmap1);
    assertThat(pd.placeHolderDrawable).isNull();
    assertThat(pd.animating).isFalse();

    pd.setBitmap(bitmap2, MEMORY, false);
    assertThat(pd.bitmapDrawable.getBitmap()).isSameAs(bitmap2);
    assertThat(pd.placeHolderDrawable).isNull();
    assertThat(pd.animating).isFalse();
  }

  @Test public void withBitmapRecycleToBitmap() {
    PicassoDrawable pd = new PicassoDrawable(context, bitmap1, MEMORY, false, false);
    BitmapDrawable bitmapDrawable = pd.bitmapDrawable;
    assertThat(bitmapDrawable.getBitmap()).isSameAs(bitmap1);
    assertThat(pd.placeHolderDrawable).isNull();
    assertThat(pd.animating).isFalse();

    pd.setBitmap(bitmap2, DISK, false);
    assertThat(pd.bitmapDrawable.getBitmap()).isSameAs(bitmap2);
    assertThat(pd.placeHolderDrawable).isSameAs(bitmapDrawable);
    assertThat(pd.animating).isTrue();
  }
}
