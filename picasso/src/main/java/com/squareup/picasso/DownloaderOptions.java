package com.squareup.picasso;

import java.util.HashMap;

public class DownloaderOptions {
  private final HashMap<String, String> requestProperties = new HashMap<String, String>();

  private boolean loadFromLocalCacheOnly;

  /**
   * Whether or not this request should only load from local cache.
   */
  boolean loadFromLocalCacheOnly() {
    return loadFromLocalCacheOnly;
  }

  /**
   * Whether or not this request should only load from local cache.
   */
  void setLoadFromLocalCacheOnly(boolean loadFromLocalCacheOnly) {
    this.loadFromLocalCacheOnly = loadFromLocalCacheOnly;
  }

  /**
   * A set of requestProperties to apply to a download.
   */
  HashMap<String, String> getRequestProperties() {
    return requestProperties;
  }

  /**
   * Adds a request property to be applied if this request results in a download.
   *
   * @param name  The name of the property to apply. Example: "Referer"
   * @param value The value of the property.
   */
  public DownloaderOptions setRequestProperty(String name, String value) {
    if (name != null) {
      requestProperties.put(name, value);
    }

    return this;
  }
}
