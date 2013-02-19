package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

public class Request implements Runnable {
  private static final int PROCESS_RESULT = 1;

  private static final Handler handler = new Handler(Looper.getMainLooper()) {
    @Override public void handleMessage(Message msg) {
      if (msg.what == PROCESS_RESULT) {
        Request request = (Request) msg.obj;
        if (request.future.isCancelled()) return;

        ImageView imageView = request.target.get();
        if (imageView != null) {
          imageView.setImageBitmap(request.result);
        }
      }
    }
  };

  private final Picasso picasso;
  private final String path;
  private final WeakReference<ImageView> target;

  private Future<?> future;
  private Bitmap result;

  Request(Picasso picasso, String path, ImageView imageView) {
    this.picasso = picasso;
    this.path = path;
    this.target = new WeakReference<ImageView>(imageView);
  }

  @Override public void run() {
    Loader loader = picasso.getLoader();
    InputStream stream = null;
    try {
      stream = loader.load(path);
      result = BitmapFactory.decodeStream(stream);

      handler.sendMessage(handler.obtainMessage(PROCESS_RESULT, this));
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  Future<?> getFuture() {
    return future;
  }

  ImageView getTarget() {
    return target.get();
  }

  void setFuture(Future<?> future) {
    this.future = future;
  }

  public static class Builder {
    private final Picasso picasso;
    private final String path;
    private Drawable placeholderDrawable;
    private int placeholderResId;

    public Builder(Picasso picasso, String path) {
      this.picasso = picasso;
      this.path = path;
    }

    public Builder placeholder(int placeholderResId) {
      if (placeholderDrawable != null) {
        throw new IllegalStateException("TODO");
      }
      this.placeholderResId = placeholderResId;
      return this;
    }

    public Builder placeholder(Drawable placeholderDrawable) {
      if (placeholderResId != 0) {
        throw new IllegalStateException("TODO");
      }
      this.placeholderDrawable = placeholderDrawable;
      return this;
    }

    public void into(ImageView target) {
      if (target == null) {
        throw new IllegalStateException("TODO");
      }

      if (placeholderDrawable != null) {
        target.setImageDrawable(placeholderDrawable);
      }

      if (placeholderResId != 0) {
        target.setImageResource(placeholderResId);
      }

      picasso.submit(new Request(picasso, path, target));
    }
  }
}
