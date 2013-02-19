package com.squareup.picasso;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import com.squareup.picasso.external.LruCache;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB_MR1;

public class LruMemoryCache implements Cache {

  private final LruCache<String, Bitmap> lruCache;

  public LruMemoryCache(Context context) {
    this(calculateMaxSize(context));
  }

  public LruMemoryCache(int maxSize) {
    lruCache = new LruCache<String, Bitmap>(maxSize) {
      @Override protected int sizeOf(String key, Bitmap value) {
        if (SDK_INT >= HONEYCOMB_MR1) {
          return BitmapHoneycombMR1.getByteCount(value);
        }
        return value.getRowBytes() * value.getHeight();
      }
    };
  }

  @Override public Bitmap get(String key) {
    return lruCache.get(key);
  }

  @Override public void set(String key, Bitmap bitmap) {
    lruCache.put(key, bitmap);
  }

  private static int calculateMaxSize(Context context) {
    ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
    return 1024 * 1024 * am.getMemoryClass() / 6;
  }

  private static class BitmapHoneycombMR1 {
    static int getByteCount(Bitmap bitmap) {
      return bitmap.getByteCount();
    }
  }
}
