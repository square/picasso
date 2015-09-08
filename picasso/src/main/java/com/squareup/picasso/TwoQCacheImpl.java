package com.squareup.picasso;

import android.graphics.Bitmap;
import java.util.HashMap;
import java.util.Map;
import static com.squareup.picasso.Utils.KEY_SEPARATOR;

/**
 * Created by v.kulibaba on 04/09/15.
 */
public class TwoQCacheImpl extends TwoQCache<String, Bitmap> implements Cache {
  /**
   * Two queues cache
   *
   * @param maxSize for caches that do not override {@link #sizeOf}, this is
   *                this is the maximum sum of the sizes of the entries in this cache.
   */
  public TwoQCacheImpl(int maxSize) {
    super(maxSize);
  }

  @Override
  public Bitmap get(String key) {
    return super.get(key);
  }

  // implement Picasso method set
  @Override
  public void set(String key, Bitmap bitmap) {
    super.put(key, bitmap);
  }

  // implement Picasso method clear
  public void clear() {
    evictAll();
  }

  // implement Picasso method clearKeyUri
  public void clearKeyUri(String uri) {
    int uriLength = uri.length();
    synchronized (this) {
      HashMap<String, Bitmap> copyMap = new HashMap<String, Bitmap>(map);
      for (Map.Entry<String, Bitmap> entry : copyMap.entrySet()) {
        String key = entry.getKey();
        Bitmap value = entry.getValue();
        int newlineIndex = key.indexOf(KEY_SEPARATOR);
        if (newlineIndex == uriLength && key.substring(0, newlineIndex).equals(uri)) {
          this.remove(entry.getKey());
        }
      }
    }
  }

  @Override
  protected int sizeOf(String key, Bitmap value) {
    final int bitmapSize = Utils.getBitmapBytes(value) / 1024;
    return bitmapSize == 0 ? 1 : bitmapSize;
  }
}
