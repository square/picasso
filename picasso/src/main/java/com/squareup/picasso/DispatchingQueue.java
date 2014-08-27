package com.squareup.picasso;

import android.os.Handler;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A queue that manages dispatching the BitmapHunter result
 *
 * @author Hannes Dorfmann
 */
public class DispatchingQueue {

  final Map<BitmapHunter, DispatchJob> hunterMap;
  final Queue<DispatchJob> jobQueue;
  final Handler handler;
  boolean dispatching;

  public DispatchingQueue(Handler handler) {
    this.handler = handler;
    this.jobQueue = new ConcurrentLinkedQueue<DispatchJob>();
    this.hunterMap = new ConcurrentHashMap<BitmapHunter, DispatchJob>();
    this.dispatching = true;
  }

  public void interruptDispatching() {
    Utils.checkMain();
    dispatching = false;
  }

  public void continueDispatching() {
    Utils.checkMain();
    dispatching = true;
    dispatchNextFromQueue();
  }

  public boolean isDispatchingEnabled() {
    return dispatching;
  }

  private void scheduleJob(DispatchJob job) {
    boolean added = jobQueue.offer(job);

    if (added) {
      hunterMap.put(job.getBitmapHunter(), job);
    }
  }

  public void dispatchComplete(BitmapHunter hunter) {

    if (isDispatchingEnabled()) {
      // dispatch directly; avoid object creation overhead
      handler.sendMessage(handler.obtainMessage(Dispatcher.HUNTER_COMPLETE, hunter));
    } else {
      // dispatching is disabled temporally
      CompleteDispatchJob job = new CompleteDispatchJob(handler, hunter);
      scheduleJob(job);
    }
  }

  public void dispatchFailed(BitmapHunter hunter) {

    if (isDispatchingEnabled()) {
      // dispatch directly; avoid object creation overhead
      handler.sendMessage(handler.obtainMessage(Dispatcher.HUNTER_DECODE_FAILED, hunter));
    } else {
      // dispatching is disabled temporally
      FailedDispatchJob job = new FailedDispatchJob(handler, hunter);
      scheduleJob(job);
    }
  }

  public void dispatchNextFromQueue() {

    while (!jobQueue.isEmpty()) {
      DispatchJob job = jobQueue.poll();

      if (job != null) {
        hunterMap.remove(job.getBitmapHunter());
        job.dispatch();
      }
    }
  }

  public void dequeue(BitmapHunter hunter) {

    DispatchJob job = hunterMap.get(hunter);
    if (job != null) {
      // Remove the waiting jo from queue
      jobQueue.remove(job);
      hunterMap.remove(hunter);
    }
  }

  public void clear() {
    jobQueue.clear();
    hunterMap.clear();
  }

  /**
   * Base class for dispatch jobs
   */
  abstract static class DispatchJob {

    protected final Handler handler;
    protected final BitmapHunter hunter;

    protected DispatchJob(Handler handler, BitmapHunter hunter) {
      this.handler = handler;
      this.hunter = hunter;
    }

    public BitmapHunter getBitmapHunter() {
      return hunter;
    }

    /**
     * Dispatches the message
     */
    public abstract void dispatch();
  }

  /**
   * A job that will dispatch that the BitmapHunter has completetd his job
   */
  static class CompleteDispatchJob extends DispatchJob {

    protected CompleteDispatchJob(Handler handler, BitmapHunter hunter) {
      super(handler, hunter);
    }

    public void dispatch() {
      handler.sendMessage(handler.obtainMessage(Dispatcher.HUNTER_COMPLETE, hunter));
    }
  }

  /**
   * A job to dispatch that the BitmapHunter has failed
   */
  static class FailedDispatchJob extends DispatchJob {

    FailedDispatchJob(Handler handler, BitmapHunter hunter) {
      super(handler, hunter);
    }

    public void dispatch() {
      handler.sendMessage(handler.obtainMessage(Dispatcher.HUNTER_DECODE_FAILED, hunter));
    }
  }
}
