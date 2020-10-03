package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.squareup.picasso.Utils.KEY_SEPARATOR;

public abstract class BaseLruExifCache<T> implements Cache {
    final LinkedHashMap<String, Bitmap> map; // uri with rotation info etc
    final LinkedHashMap<Bitmap, String> linkMap; // raw uri
    final LinkedHashMap<String, T> metadataMap; // raw uri

    private final int maxSize;

    private int size;
    private int putCount;
    private int evictionCount;
    private int hitCount;
    private int missCount;

    /** Create a cache using an appropriate portion of the available RAM as the maximum size. */
    public BaseLruExifCache(Context context) {
        this(Utils.calculateMemoryCacheSize(context));
    }

    /** Create a cache with a given maximum size in bytes. */
    public BaseLruExifCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Max size must be positive.");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
        this.linkMap = new LinkedHashMap<Bitmap, String>(0, 0.75f, true);
        metadataMap = new LinkedHashMap<String, T>(0, 0.75f, true);
    }

    public T getMetadata(String uri) {
        if (uri == null) {
            throw new NullPointerException("uri == null");
        }
        synchronized (this) {
            return metadataMap.get(uri);
        }
    }

    public T getMetadata(Bitmap bitmap) {
        if (bitmap == null) {
            throw new NullPointerException("bitmap == null");
        }
        synchronized (this) {
            return metadataMap.get(linkMap.get(bitmap));
        }
    }

    public void setMetadata(String uri, T metadata) {
        if (uri == null || metadata == null) {
            throw new NullPointerException("key == null || metadata == null");
        }
        synchronized (this) {
            List<Bitmap> bitmaps = findAll(uri);
            for(Bitmap bitmap : bitmaps) {
                linkMap.put(bitmap, uri);
            }
            metadataMap.put(uri, metadata);
        }
    }

    private boolean keyMatchesRawUri(String mapKey, String rawUri) {
        int newlineIndex = mapKey.indexOf(KEY_SEPARATOR);
        return newlineIndex == rawUri.length() && mapKey.substring(0, newlineIndex).equals(rawUri);
    }

    private String getUriFromKey(String mapKey) {
        int newlineIndex = mapKey.indexOf(KEY_SEPARATOR);
        return mapKey.substring(0, newlineIndex);
    }

    private List<Bitmap> findAll(String uri) {

        List<Bitmap> matchingBitmapsForUri = new ArrayList<>();

        for (Iterator<Map.Entry<String, Bitmap>> i = map.entrySet().iterator(); i.hasNext();) {

            Map.Entry<String, Bitmap> entry = i.next();
            String key = entry.getKey();
            Bitmap value = entry.getValue();

            if(keyMatchesRawUri(key, uri)) {
                matchingBitmapsForUri.add(value);
            }
        }

        return matchingBitmapsForUri;

    }

    @Override public Bitmap get(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        Bitmap mapValue;
        synchronized (this) {
            mapValue = map.get(key);
            if (mapValue != null) {
                hitCount++;
                T metadata = getMetadata(mapValue);
                if(metadata != null) {
                    String uri = linkMap.get(mapValue);
                    onImageRetrieved(uri, mapValue, metadata);
                    Log.d("LruExifCache", "Loading Metadata for : " + uri/*, new Exception().fillInStackTrace()*/);
                }
                return mapValue;
            }
            missCount++;
        }

        return null;
    }

    protected abstract void onImageRetrieved(String uri, Bitmap bitmap, T metadata);

    @Override public void set(String key, Bitmap bitmap) {
        if (key == null || bitmap == null) {
            throw new NullPointerException("key == null || bitmap == null");
        }

        Bitmap previous;
        synchronized (this) {
            putCount++;
            size += Utils.getBitmapBytes(bitmap);
            previous = map.put(key, bitmap);
            String uri = getUriFromKey(key);
            linkMap.put(bitmap, uri);
            if (previous != null) {
                size -= Utils.getBitmapBytes(previous);
            }
        }

        trimToSize(maxSize);
    }

    private void trimToSize(int maxSize) {
        while (true) {
            String key;
            Bitmap value;
            synchronized (this) {
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(
                            getClass().getName() + ".sizeOf() is reporting inconsistent results!");
                }

                if (size <= maxSize || map.isEmpty()) {
                    break;
                }

                Map.Entry<String, Bitmap> toEvict = map.entrySet().iterator().next();
                key = toEvict.getKey();
                value = toEvict.getValue();
                // remove the bitmap
                Bitmap bitmap = map.remove(key);
                if(bitmap != null) {
                    // remove the link to metadata
                    String uri = linkMap.remove(bitmap);
                    if(uri != null) {
                        // remove the metadata
                        metadataMap.remove(uri);
                    }
                }
                size -= Utils.getBitmapBytes(value);
                evictionCount++;
            }
        }
    }

    /** Clear the cache. */
    public final void evictAll() {
        trimToSize(-1); // -1 will evict 0-sized elements
    }

    @Override public final synchronized int size() {
        return size;
    }

    @Override public final synchronized int maxSize() {
        return maxSize;
    }

    @Override public final synchronized void clear() {
        evictAll();
    }

    @Override public final synchronized void clearKeyUri(String uri) {
        boolean sizeChanged = false;
        for (Iterator<Map.Entry<String, Bitmap>> i = map.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String, Bitmap> entry = i.next();
            String key = entry.getKey();
            Bitmap value = entry.getValue();
            if (keyMatchesRawUri(key, uri)) {
                i.remove();
                metadataMap.remove(key);
                linkMap.remove(value);
                size -= Utils.getBitmapBytes(value);
                sizeChanged = true;
            }
        }
        if (sizeChanged) {
            trimToSize(maxSize);
        }
    }

    /** Returns the number of times {@link #get} returned a value. */
    public final synchronized int hitCount() {
        return hitCount;
    }

    /** Returns the number of times {@link #get} returned {@code null}. */
    public final synchronized int missCount() {
        return missCount;
    }

    /** Returns the number of times {@link #set(String, Bitmap)} was called. */
    public final synchronized int putCount() {
        return putCount;
    }

    /** Returns the number of values that have been evicted. */
    public final synchronized int evictionCount() {
        return evictionCount;
    }
}
