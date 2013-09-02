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

import android.view.ViewTreeObserver;
import android.widget.ImageView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.TestUtils.TRANSFORM_REQUEST_ANSWER;
import static com.squareup.picasso.TestUtils.URI_1;
import static com.squareup.picasso.TestUtils.mockCallback;
import static com.squareup.picasso.TestUtils.mockFitImageViewTarget;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@SuppressWarnings("deprecation")
public class DeferredRequestCreatorTest {

  @Captor ArgumentCaptor<Action> actionCaptor;

  @Before public void setUp() throws Exception {
    initMocks(this);
  }

  @Test public void initAttachesLayoutListener() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    ViewTreeObserver observer = target.getViewTreeObserver();
    DeferredRequestCreator request = new DeferredRequestCreator(mock(RequestCreator.class), target);
    verify(observer).addOnPreDrawListener(request);
  }

  @Test public void cancelRemovesLayoutListener() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    ViewTreeObserver observer = target.getViewTreeObserver();
    DeferredRequestCreator request = new DeferredRequestCreator(mock(RequestCreator.class), target);
    request.cancel();
    verify(observer).removeOnPreDrawListener(request);
  }

  @Test public void cancelClearsCallback() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    Callback callback = mockCallback();
    DeferredRequestCreator request =
        new DeferredRequestCreator(mock(RequestCreator.class), target, callback);
    assertThat(request.callback).isNotNull();
    request.cancel();
    assertThat(request.callback).isNull();
  }

  @Test public void onLayoutSkipsIfTargetIsNull() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    RequestCreator creator = mock(RequestCreator.class);
    DeferredRequestCreator request = new DeferredRequestCreator(creator, target);
    ViewTreeObserver viewTreeObserver = target.getViewTreeObserver();
    request.target.clear();
    request.onPreDraw();
    verifyZeroInteractions(creator);
    verify(viewTreeObserver).addOnPreDrawListener(request);
    verifyNoMoreInteractions(viewTreeObserver);
  }

  @Test public void onLayoutSkipsIfViewTreeObserverIsDead() throws Exception {
    ImageView target = mockFitImageViewTarget(false);
    RequestCreator creator = mock(RequestCreator.class);
    DeferredRequestCreator request = new DeferredRequestCreator(creator, target);
    ViewTreeObserver viewTreeObserver = target.getViewTreeObserver();
    request.onPreDraw();
    verify(viewTreeObserver).addOnPreDrawListener(request);
    verify(viewTreeObserver).isAlive();
    verifyNoMoreInteractions(viewTreeObserver);
    verifyZeroInteractions(creator);
  }

  @Test public void waitsForAnotherLayoutIfWidthOrHeightIsZero() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    when(target.getMeasuredWidth()).thenReturn(0);
    when(target.getMeasuredHeight()).thenReturn(0);
    RequestCreator creator = mock(RequestCreator.class);
    DeferredRequestCreator request = new DeferredRequestCreator(creator, target);
    request.onPreDraw();
    verify(target.getViewTreeObserver(), never()).removeOnPreDrawListener(request);
    verifyZeroInteractions(creator);
  }

  @Test public void cancelSkipsWithNullTarget() throws Exception {
    ImageView target = mockFitImageViewTarget(true);
    RequestCreator creator = mock(RequestCreator.class);
    DeferredRequestCreator request = new DeferredRequestCreator(creator, target);
    request.target.clear();
    request.cancel();
    verify(target.getViewTreeObserver(), never()).removeOnPreDrawListener(request);
  }

  @Test public void cancelSkipsIfViewTreeObserverIsDead() throws Exception {
    ImageView target = mockFitImageViewTarget(false);
    RequestCreator creator = mock(RequestCreator.class);
    DeferredRequestCreator request = new DeferredRequestCreator(creator, target);
    request.cancel();
    verify(target.getViewTreeObserver(), never()).removeOnPreDrawListener(request);
  }

  @Test public void onGlobalLayoutSubmitsRequestAndCleansUp() throws Exception {
    Picasso picasso = mock(Picasso.class);
    when(picasso.transformRequest(any(Request.class))).thenAnswer(TRANSFORM_REQUEST_ANSWER);

    RequestCreator creator = new RequestCreator(picasso, URI_1, 0);

    ImageView target = mockFitImageViewTarget(true);
    when(target.getMeasuredWidth()).thenReturn(100);
    when(target.getMeasuredHeight()).thenReturn(100);

    ViewTreeObserver observer = target.getViewTreeObserver();

    DeferredRequestCreator request = new DeferredRequestCreator(creator, target);
    request.onPreDraw();

    verify(observer).removeOnPreDrawListener(request);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());

    Action value = actionCaptor.getValue();
    assertThat(value).isInstanceOf(ImageViewAction.class);
    assertThat(value.getData().targetWidth).isEqualTo(100);
    assertThat(value.getData().targetHeight).isEqualTo(100);
  }
}
