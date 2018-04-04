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
package com.squareup.picasso3;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static com.squareup.picasso3.Picasso.Priority;

abstract class Action<T> {
  static class RequestWeakReference<M> extends WeakReference<M> {
    final Action action;

    RequestWeakReference(Action action, M referent, ReferenceQueue<? super M> q) {
      super(referent, q);
      this.action = action;
    }
  }

  final Picasso picasso;
  final Request request;
  final WeakReference<T> target;
  final Target<T> wrapper;

  boolean willReplay;
  boolean cancelled;

  Action(Picasso picasso, Target<T> wrapper, Request request) {
    this.picasso = picasso;
    this.request = request;
    if (wrapper == null) {
      this.target = null;
    } else {
      this.target = new RequestWeakReference<>(this, wrapper.target, picasso.referenceQueue);
      // Release the reference.
      wrapper.target = null;
    }
    this.wrapper = wrapper;
  }

  abstract void complete(RequestHandler.Result result);

  abstract void error(Exception e);

  void cancel() {
    cancelled = true;
  }

  Request getRequest() {
    return request;
  }

  T getTarget() {
    return target == null ? null : target.get();
  }

  String getKey() {
    return request.key;
  }

  boolean isCancelled() {
    return cancelled;
  }

  boolean willReplay() {
    return willReplay;
  }

  Picasso getPicasso() {
    return picasso;
  }

  Priority getPriority() {
    return request.priority;
  }

  Object getTag() {
    return request.tag != null ? request.tag : this;
  }
}
