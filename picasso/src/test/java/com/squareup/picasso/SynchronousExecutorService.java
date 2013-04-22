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
