package com.squareup.picasso;

import android.os.Looper;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class Utils {

  private Utils() {
    // No instances.
  }

  static void checkNotMain() {
    if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
      throw new IllegalStateException("Method call should not happen from the main thread.");
    }
  }

  static String createMd5Hash(String text) {
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

  static byte[] getBytes(String string) {
    return string.getBytes(Charset.forName("UTF-8"));
  }
}
