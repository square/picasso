package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.squareup.okhttp.internal.DiskLruCache;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This cache is a {@link LruCache} backed by
 * a {@link DiskLruCache}. It can be used to cache
 * thumbnail of images, i.e. cache images on disk after
 * transformations.
 * Shamelessly inspired by : http://stackoverflow.com/a/10235381/693752
 *
 * @author Stephane Nicolas
 * @author Carlos Sessa
 */
public class ThumbnailCache implements Cache {
  public static final int IO_BUFFER_SIZE = 8 * 1024;

  private DiskLruCache diskLruCache;
  private static final Bitmap.CompressFormat COMPRESS_FORMAT = Bitmap.CompressFormat.WEBP;
  private static final int COMPRESS_QUALITY = 100;

  private int putCount;
  private int hitCount;
  private int missCount;

  public ThumbnailCache(DiskLruCache diskLruCache) {
    this.diskLruCache = diskLruCache;
  }

  @Override public Bitmap get(String key) {
    key = sanitize(key);
    Bitmap bitmap = getBitmap(key);

    if (bitmap != null) {
      hitCount++;
    } else {
      missCount++;
    }

    return bitmap;
  }

  @Override public void set(String key, Bitmap bitmap) {
    key = sanitize(key);
    setInDiskCache(key, bitmap);
  }

  @Override public long size() {
    return diskLruCache.size();
  }

  @Override public long maxSize() {
    return diskLruCache.getMaxSize();
  }

  @Override public void clear() {
    try {
      diskLruCache.delete();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String sanitize(String key) {
    key = key.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    if (key.length() > 64) {
      key = key.substring(key.length() - 64);
    }
    return key;
  }

  public void setInDiskCache(String key, Bitmap bitmap) {
    putCount++;
    DiskLruCache.Editor editor = null;
    try {
      editor = diskLruCache.edit(key);
      if (editor == null) {
        return;
      }

      if (writeBitmapToFile(bitmap, editor)) {
        diskLruCache.flush();
        editor.commit();
      } else {
        editor.abort();
      }
    } catch (IOException e) {
      try {
        if (editor != null) {
          editor.abort();
        }
      } catch (IOException ignored) {
      }
    }
  }

  public Bitmap getBitmap(String key) {
    key = sanitize(key);
    Bitmap bitmap = null;
    DiskLruCache.Snapshot snapshot = null;
    try {

      snapshot = diskLruCache.get(key);
      if (snapshot == null) {
        return null;
      }
      final InputStream in = snapshot.getInputStream(0);
      if (in != null) {
        final BufferedInputStream buffIn = new BufferedInputStream(in, IO_BUFFER_SIZE);
        bitmap = BitmapFactory.decodeStream(buffIn);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }

    return bitmap;
  }

  private boolean writeBitmapToFile(Bitmap bitmap, DiskLruCache.Editor editor)
      throws IOException {
    OutputStream out = null;
    try {
      out = new BufferedOutputStream(editor.newOutputStream(0), IO_BUFFER_SIZE);

      return bitmap.compress(COMPRESS_FORMAT, COMPRESS_QUALITY, out);
    } finally {
      if (out != null) {
        out.close();
      }
    }
  }
}
