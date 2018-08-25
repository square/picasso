package com.squareup.picasso3.provider;

import android.annotation.SuppressLint;
import android.content.Context;
import com.squareup.picasso3.Initializer;
import com.squareup.picasso3.Picasso;

/**
 * This instance is automatically initialized with defaults that are suitable to most
 * implementations.
 * <ul>
 * <li>LRU memory cache of 15% the available application RAM</li>
 * <li>Disk cache of 2% storage space up to 50MB but no less than 5MB. (Note: this is only
 * available on API 14+ <em>or</em> if you are using a standalone library that provides a disk
 * cache on all API levels like OkHttp)</li>
 * <li>Three download threads for disk and network access.</li>
 * </ul>
 * <p>
 * If these settings do not meet the requirements of your application, you can construct your own
 * with full control over the configuration by using {@link Picasso.Builder} to create a
 * customized {@link Picasso} instance.
 * <p>
 * Note: Production apps could instead use dependency injection to provide their Picasso instances.
 */
public final class PicassoProvider {
  @SuppressLint("StaticFieldLeak")
  private static volatile Picasso instance;

  @Initializer
  public static Picasso get() {
    if (instance == null) {
      synchronized (PicassoProvider.class) {
        if (instance == null) {
          Context autoContext = PicassoContentProvider.context;
          if (autoContext == null) {
            throw new NullPointerException("context == null");
          }
          instance = new Picasso.Builder(autoContext).build();
        }
      }
    }
    return instance;
  }

  private PicassoProvider() {
    throw new AssertionError("No instances.");
  }
}
