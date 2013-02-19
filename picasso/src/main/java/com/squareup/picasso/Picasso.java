package com.squareup.picasso;

import android.widget.ImageView;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Picasso {
  private static Picasso singleton = null;

  private final Loader loader;
  private final ExecutorService service;
  private final Map<ImageView, Request> targetsToRequests = new WeakHashMap<ImageView, Request>();

  public Picasso() {
    this.loader = new ApacheHttpLoader();
    this.service = Executors.newSingleThreadExecutor();
  }

  void submit(Request request) {
    ImageView target = request.getTarget();
    if (target == null) return;

    Request existing = targetsToRequests.remove(target);
    if (existing != null) {
      existing.getFuture().cancel(true);
    }

    targetsToRequests.put(target, request);
    request.setFuture(service.submit(request));
  }

  public Loader getLoader() {
    return loader;
  }

  public static Request.Builder load(String path) {
    if (singleton == null) {
      singleton = new Picasso();
    }
    return new Request.Builder(singleton, path);
  }
}
