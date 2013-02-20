package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.squareup.picasso.external.DiskLruCache;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import static com.squareup.picasso.Utils.checkNotMain;

public class LruDiskCache implements Cache {

  private static final String DIR = "picasso";
  private static final int QUALITY = 100;
  private static final int MAX_SIZE = 1024 * 1024 * 10; // 10MB

  private final DiskLruCache diskLruCache;

  public LruDiskCache(Context context) throws IOException {
    this(new File(context.getCacheDir(), DIR), MAX_SIZE);
  }

  public LruDiskCache(File directory, long maxSize) throws IOException {
    diskLruCache = DiskLruCache.open(directory, 1, 1, maxSize);
  }

  @Override public Bitmap get(String key) throws IOException {
    checkNotMain();
    DiskLruCache.Snapshot snapshot = null;
    try {
      snapshot = diskLruCache.get(Utils.createMd5Hash(key));
      return snapshot != null ? BitmapFactory.decodeStream(snapshot.getInputStream(0)) : null;
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }
  }

  @Override public void set(String key, Bitmap bitmap) throws IOException {
    checkNotMain();
    DiskLruCache.Editor editor = null;
    try {
      editor = diskLruCache.edit(Utils.createMd5Hash(key));
      OutputStream stream = editor.newOutputStream(0);
      bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, stream);
      editor.commit();
      diskLruCache.flush();
    } finally {
      if (editor != null) {
        editor.abortUnlessCommitted();
      }
    }
  }
}
