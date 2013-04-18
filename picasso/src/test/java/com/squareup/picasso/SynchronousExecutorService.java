package com.squareup.picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

class SynchronousExecutorService extends AbstractExecutorService {
  final List<Runnable> runnableList = new ArrayList<Runnable>();

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
    runnableList.add(runnable);
  }

  public void flush() {
    for (Runnable runnable : runnableList) {
      runnable.run();
    }
    runnableList.clear();
  }

  public void executeFirst() {
    runnableList.remove(0).run();
  }
}
