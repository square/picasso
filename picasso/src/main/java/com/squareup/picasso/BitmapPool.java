package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Interface that allows the reuse of already allocated bitmap memory for different bitmap content
 * This is different from a normal {@link Cache}. A normal cache allow reuse of the same bitmap
 * with the same content.
 *
 * When used together with a {@link Cache}, the Cache must also implement {@link BitmapPoolAware}
 */
public interface BitmapPool {
    /**
     * tries to find a {@link Bitmap}:
     * - that is not in use (refCount==0)
     * - and is compatible with the {@link android.graphics.BitmapFactory.Options}
     * Returns null if such a bitmap does not exist
     *
     * @param options
     * @return Bitmap that is not in use, and is compatible with the options. Returns null
     * if such a bitmap does not exist
     */
    @Nullable
    Bitmap tryFindBitmap(BitmapFactory.Options options);
    /**
     * Increments the reference count for the bitmap.
     * This should be called when:
     * - the bitmap is first created, and returned to a caller
     * - a copy of the bitmap reference is returned to a caller
     *
     * @param bitmap
     */
    void incrementRefCount(Bitmap bitmap);
    /**
     * Decrements the reference count for the bitmap.
     * This should be called when:
     * - the bitmap is no longer used by a view
     * - when you could normally call {@see Bitmap.recycle()}
     *
     * If the refcount becomes 0 the bitmap will be made available for reuse
     * @param bitmap
     */
    void decrementRefCount(Bitmap bitmap);
}
