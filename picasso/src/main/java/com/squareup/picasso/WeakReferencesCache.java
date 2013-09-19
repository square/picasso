package com.squareup.picasso;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.HashMap;

/**
 * This class is an small cache that store the data in a softreferences map.
 */
public class WeakReferencesCache implements Cache {

    final HashMap<String, WeakReference<Bitmap>> bitmapCache
            = new HashMap<String, WeakReference<Bitmap>>();
    private int hitCount;
    private int missCount;
    private int putCount;

    @Override
    public Bitmap get(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        Bitmap bitmap = null;

        synchronized (this) {
            WeakReference<Bitmap> mapValue = bitmapCache.get(key);
            if (mapValue != null) {    //exist the key
                if (mapValue.get() != null) { //the image has been not purged
                    bitmap = mapValue.get();
                    hitCount++;
                }
            }

            if (bitmap == null) {
                missCount++;
            }
        }

        return bitmap;
    }

    @Override
    public void set(String key, Bitmap bitmap) {
        if (key == null || bitmap == null) {
            throw new NullPointerException("key == null || bitmap == null");
        }

        synchronized (this) {
            putCount++;
            bitmapCache.put(key, new WeakReference<Bitmap>(bitmap));
        }
    }

    @Override
    public int size() {
        //We can not calculate the actual size, because we can don't know when an image has been
        //purged.
        return -1;
    }

    @Override
    public int maxSize() {
        //We can not calculate the max size, The garbage collector define it.
        return -1;
    }

    @Override
    public void clear() {
        bitmapCache.clear();
    }

    final int putCount() {
        return putCount;
    }

    final int hitCount() {
        return hitCount;
    }

    final int missCount() {
        return missCount;
    }

    /**
     * This is a debug method, to simulate that the garbage collector is called and the references
     * have changed it.
     */
    final void emulateGarbageRecollection() {
        for (WeakReference<Bitmap> bitmapWeakReference : bitmapCache.values()) {
            bitmapWeakReference.clear();
        }
    }
}
