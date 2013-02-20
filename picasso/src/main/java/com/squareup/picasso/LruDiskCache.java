package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.squareup.picasso.external.DiskLruCache;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.squareup.picasso.Picasso.checkNotMain;
import static com.squareup.picasso.external.DiskLruCache.open;

public class LruDiskCache implements Cache {

  private static final String DIR = "picasso";
  private static final int QUALITY = 100;
  private static final int MAX_SIZE = 1024 * 1024 * 10; // 10MB

  private final DiskLruCache diskLruCache;

  public LruDiskCache(Context context) throws IOException {
    this(new File(context.getCacheDir(), DIR), MAX_SIZE);
  }

  public LruDiskCache(File directory, long maxSize) throws IOException {
    diskLruCache = open(directory, 1, 1, maxSize);
  }

  @Override public Bitmap get(String key) throws IOException {
    checkNotMain();
    DiskLruCache.Snapshot snapshot = null;
    try {
      snapshot = diskLruCache.get(createMd5Hash(key));
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
      editor = diskLruCache.edit(createMd5Hash(key));
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

  private static String createMd5Hash(String text) {
    try {
      // Create MD5 Hash.
      MessageDigest digest = MessageDigest.getInstance("MD5");
      digest.update(getBytes(text));
      byte messageDigest[] = digest.digest();

      // Create Hex String.
      StringBuilder hexString = new StringBuilder();
      for (byte b : messageDigest) {
        hexString.append(Integer.toHexString(0xff & b));
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("Unable to construct MD5 hash!");
    }
  }

  private static byte[] getBytes(String string) {
    return string.getBytes(Charset.forName("UTF-8"));
  }
}
