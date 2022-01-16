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

import com.squareup.picasso3.PicassoExecutorService
import com.squareup.picasso3.PicassoExecutorService.PicassoFutureTask
import com.squareup.picasso3.BitmapHunter
import com.squareup.picasso3.Picasso
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * The default [java.util.concurrent.ExecutorService] used for new [Picasso] instances.
 *
 *
 * Exists as a custom type so that we can differentiate the use of defaults versus a user-supplied
 * instance.
 */
internal class PicassoExecutorService(threadFactory: ThreadFactory?) : ThreadPoolExecutor(
  DEFAULT_THREAD_COUNT, DEFAULT_THREAD_COUNT, 0, MILLISECONDS,
  PriorityBlockingQueue(), threadFactory
) {
  override fun submit(task: Runnable): Future<*> {
    val ftask = PicassoFutureTask(task as BitmapHunter)
    execute(ftask)
    return ftask
  }

  private class PicassoFutureTask internal constructor(private val hunter: BitmapHunter) :
    FutureTask<BitmapHunter?>(
      hunter, null
    ), Comparable<PicassoFutureTask> {
    override fun compareTo(other: PicassoFutureTask): Int {
      val p1 = hunter.priority
      val p2 = other.hunter.priority

      // High-priority requests are "lesser" so they are sorted to the front.
      // Equal priorities are sorted by sequence number to provide FIFO ordering.
      return if (p1 == p2) hunter.sequence - other.hunter.sequence else p2.ordinal - p1.ordinal
    }
  }

  companion object {
    private const val DEFAULT_THREAD_COUNT = 3
  }
}