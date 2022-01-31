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
package com.squareup.picasso3

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.Captor
import org.mockito.ArgumentCaptor
import org.junit.Before
import com.google.common.truth.Truth.assertThat
import com.squareup.picasso3.TestUtils.TRANSFORM_REQUEST_ANSWER
import com.squareup.picasso3.TestUtils.mockCallback
import com.squareup.picasso3.TestUtils.mockFitImageViewTarget
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations.initMocks

@RunWith(RobolectricTestRunner::class)
class DeferredRequestCreatorTest {
  @Captor
  internal lateinit var actionCaptor: ArgumentCaptor<Action>

  @Before fun setUp() {
    initMocks(this)
  }

  @Test fun initWhileAttachedAddsAttachAndPreDrawListener() {
    val target = mockFitImageViewTarget(true)
    val observer = target.viewTreeObserver
    val request = DeferredRequestCreator(mock(RequestCreator::class.java), target, null)
    verify(observer).addOnPreDrawListener(request)
  }

  @Test fun initWhileDetachedAddsAttachListenerWhichDefersPreDrawListener() {
    val target = mockFitImageViewTarget(true)
    `when`(target.windowToken).thenReturn(null)
    val observer = target.viewTreeObserver
    val request = DeferredRequestCreator(mock(RequestCreator::class.java), target, null)
    verify(target).addOnAttachStateChangeListener(request)
    verifyNoMoreInteractions(observer)

    // Attach and ensure we defer to the pre-draw listener.
    request.onViewAttachedToWindow(target)
    verify(observer).addOnPreDrawListener(request)

    // Detach and ensure we remove the pre-draw listener from the original VTO.
    request.onViewDetachedFromWindow(target)
    verify(observer).removeOnPreDrawListener(request)
  }

  @Test fun cancelWhileAttachedRemovesAttachListener() {
    val target = mockFitImageViewTarget(true)
    val request = DeferredRequestCreator(mock(RequestCreator::class.java), target, null)
    verify(target).addOnAttachStateChangeListener(request)
    request.cancel()
    verify(target).removeOnAttachStateChangeListener(request)
  }

  @Test fun cancelClearsCallback() {
    val target = mockFitImageViewTarget(true)
    val callback = mockCallback()
    val request = DeferredRequestCreator(mock(RequestCreator::class.java), target, callback)
    assertThat(request.callback).isNotNull()
    request.cancel()
    assertThat(request.callback).isNull()
  }

  @Test fun cancelClearsTag() {
    val target = mockFitImageViewTarget(true)
    val creator = mock(RequestCreator::class.java)
    `when`(creator.tag).thenReturn("TAG")
    val request = DeferredRequestCreator(creator, target, null)
    request.cancel()
    verify(creator).clearTag()
  }

  @Test fun onLayoutSkipsIfViewIsAttachedAndViewTreeObserverIsDead() {
    val target = mockFitImageViewTarget(false)
    val creator = mock(RequestCreator::class.java)
    val request = DeferredRequestCreator(creator, target, null)
    val viewTreeObserver = target.viewTreeObserver
    request.onPreDraw()
    verify(viewTreeObserver).addOnPreDrawListener(request)
    verify(viewTreeObserver).isAlive
    verifyNoMoreInteractions(viewTreeObserver)
    verifyZeroInteractions(creator)
  }

  @Test fun waitsForAnotherLayoutIfWidthOrHeightIsZero() {
    val target = mockFitImageViewTarget(true)
    `when`(target.width).thenReturn(0)
    `when`(target.height).thenReturn(0)
    val creator = mock(RequestCreator::class.java)
    val request = DeferredRequestCreator(creator, target, null)
    request.onPreDraw()
    verify(target.viewTreeObserver, never()).removeOnPreDrawListener(request)
    verifyZeroInteractions(creator)
  }

  @Test fun cancelSkipsIfViewTreeObserverIsDead() {
    val target = mockFitImageViewTarget(false)
    val creator = mock(RequestCreator::class.java)
    val request = DeferredRequestCreator(creator, target, null)
    request.cancel()
    verify(target.viewTreeObserver, never()).removeOnPreDrawListener(request)
  }

  @Test fun preDrawSubmitsRequestAndCleansUp() {
    val picasso = mock(Picasso::class.java)
    `when`(picasso.transformRequest(any(Request::class.java))).thenAnswer(TRANSFORM_REQUEST_ANSWER)

    val creator = RequestCreator(picasso, TestUtils.URI_1, 0)

    val target = mockFitImageViewTarget(true)
    `when`(target.width).thenReturn(100)
    `when`(target.height).thenReturn(100)

    val observer = target.viewTreeObserver

    val request = DeferredRequestCreator(creator, target, null)
    request.onPreDraw()

    verify(observer).removeOnPreDrawListener(request)
    verify(picasso).enqueueAndSubmit(actionCaptor.capture())

    val value = actionCaptor.value
    assertThat(value).isInstanceOf(ImageViewAction::class.java)
    assertThat(value.request.targetWidth).isEqualTo(100)
    assertThat(value.request.targetHeight).isEqualTo(100)
  }
}