package com.squareup.picasso;

/**
 * A class that implements this interface must call
 * {@see BitmapPool.incrementRefCount()} and {@see BitmapPool.decrementRefCount()}
 * at the moments specified in their respective documentation.
 */
public interface BitmapPoolAware {
    void setBitmapPool(BitmapPool bitmapPool);
}
