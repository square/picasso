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

import android.R;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static android.graphics.Color.RED;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso.Picasso.LoadedFrom.MEMORY;
import static com.squareup.picasso.PicassoDrawable.obtainPlaceholderName;
import static com.squareup.picasso.TestUtils.BITMAP_1;
import static com.squareup.picasso.TestUtils.BITMAP_2;
import static com.squareup.picasso.TestUtils.mockPlaceholderTransformationToReturn;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class) @Config(manifest = Config.NONE)
public class PicassoDrawableTest {
  private final Context context = Robolectric.application;
  private final Drawable placeholder = new ColorDrawable(RED);

  @Test public void createWithNoPlaceholderAnimation() {
    PicassoDrawable pd = new PicassoDrawable(context, null, BITMAP_1, DISK, false, false);
    assertThat(pd.image.getBitmap()).isSameAs(BITMAP_1);
    assertThat(pd.placeholder).isNull();
    assertThat(pd.animating).isTrue();
  }

  @Test public void createWithPlaceholderAnimation() {
    PicassoDrawable pd = new PicassoDrawable(context, placeholder, BITMAP_1, DISK, false, false);
    assertThat(pd.image.getBitmap()).isSameAs(BITMAP_1);
    assertThat(pd.placeholder).isSameAs(placeholder);
    assertThat(pd.animating).isTrue();
  }

  @Test public void setPlaceholderWithTransformationCacheHit() {
    //mock data
    PlaceholderTransformation placehoderTransformation =
        mockPlaceholderTransformationToReturn(BITMAP_2);
    List transformations = new ArrayList<PlaceholderTransformation>();

    transformations.add(placehoderTransformation);

    String placeHolderName = obtainPlaceholderName(R.drawable.picture_frame, transformations);

    Cache placeHolderCache = spy(new WeakReferencesCache());
    placeHolderCache.set(placeHolderName, BITMAP_1);

    //we need the context for create the placeholder
    ImageView imageViewTarget = spy(new ImageView(Robolectric.application));

    PicassoDrawable.setPlaceholder(imageViewTarget, R.drawable.picture_frame, null, transformations,
        placeHolderCache);

    verify(imageViewTarget).setImageBitmap(BITMAP_1);
    verify(placeHolderCache, never()).set(placeHolderName, BITMAP_2);
  }

  @Test public void setPlaceholderWithTransformationCacheMissWillUpdateCache() {
    //mock data
    PlaceholderTransformation placehoderTransformation =
        mockPlaceholderTransformationToReturn(BITMAP_2);
    List transformations = new ArrayList<PlaceholderTransformation>();

    transformations.add(placehoderTransformation);

    String placeHolderName = obtainPlaceholderName(R.drawable.picture_frame, transformations);

    Cache placeHolderCache = spy(new WeakReferencesCache());

    //we need the context for create the placeholder
    ImageView target = spy(new ImageView(Robolectric.application));

    PicassoDrawable.setPlaceholder(target, R.drawable.picture_frame, null, transformations,
        placeHolderCache);

    verify(target).setImageBitmap(BITMAP_2);
    verify(placeHolderCache).set(placeHolderName, BITMAP_2);
  }

  @Test public void createWithBitmapCacheHit() {
    PicassoDrawable pd = new PicassoDrawable(context, placeholder, BITMAP_1, MEMORY, false, false);

    assertThat(pd.image.getBitmap()).isSameAs(BITMAP_1);
    assertThat(pd.placeholder).isNull();
    assertThat(pd.animating).isFalse();
  }
}
