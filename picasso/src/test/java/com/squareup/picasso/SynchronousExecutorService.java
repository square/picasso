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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

class SynchronousExecutorService extends AbstractExecutorService {
  final List<FutureTask<?>> tasks = new ArrayList<FutureTask<?>>();

  @Override public void shutdown() {
  }

  @Override public List<Runnable> shutdownNow() {
    return null;
  }

  @Override public boolean isShutdown() {
    return false;
  }

  @Override public boolean isTerminated() {
    return false;
  }

  @Override public boolean awaitTermination(long l, @NotNull TimeUnit timeUnit)
      throws InterruptedException {
    return false;
  }

  @Override public void execute(@NotNull Runnable runnable) {
    tasks.add((FutureTask<?>) runnable);
  }

  public void flush() throws Exception {
    while (!tasks.isEmpty()) {
      executeFirst();
    }
  }

  public void executeFirst() throws Exception {
    FutureTask<?> task = tasks.remove(0);
    task.run();
    if (!task.isCancelled()) {
      task.get(); // Synchronously block to force exceptions to be thrown.
    }
  }
}
