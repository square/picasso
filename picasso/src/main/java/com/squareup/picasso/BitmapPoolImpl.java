package com.squareup.picasso;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This implementation is thread safe
 */
public class BitmapPoolImpl implements BitmapPool {
    public static final String TAG = "BitmapPool";
    private final Map<Bitmap, AtomicInteger> bitmapRefCounts = new WeakHashMap<>();
    private final List<SoftReference<Bitmap>> bitmapCandidates = new ArrayList<>();

    @Override
    @Nullable
    public Bitmap tryFindBitmap(final BitmapFactory.Options options) {
        // find a bitmap that satisfies the conditions given in options
        // callers should increment the refcount
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        synchronized (bitmapCandidates) {
            Bitmap bestCandidate = null;
            SoftReference<Bitmap> bestBitmapSoftReference = null;
            int bestCandidateScore = 0;
            Log.d(TAG, "tryFindBitmap " + options.outWidth + "x" + options.outHeight);
            for (Iterator<SoftReference<Bitmap>> it = bitmapCandidates.iterator(); it.hasNext();) {
                final SoftReference<Bitmap> bitmapSoftReference = it.next();
                Bitmap candidate = bitmapSoftReference.get();
                if (candidate == null) {
                    // candidate was garbage collected
                    it.remove();
                } else if (canUseForInBitmap(candidate, options)) {
                    Log.d(TAG, getBitmapString(candidate) + " candidate in pool");
                    int candidateScore = candidate.getWidth() * candidate.getHeight();
                    if (bestCandidateScore == 0 || bestCandidateScore > candidateScore) {
                        bestCandidateScore = candidateScore;
                        bestCandidate = candidate;
                        bestBitmapSoftReference = bitmapSoftReference;
                    }
                }
            }
            if (bestCandidate != null) {
                Log.d(TAG, getBitmapString(bestCandidate) + " returned from pool");
                if (!bitmapCandidates.remove(bestBitmapSoftReference)) {
                    Log.e(TAG, getBitmapString(bestCandidate) + " was not in the bitmappool!!!");
                }
            }
            return bestCandidate;
        }
    }


    @Override
    public void incrementRefCount(final Bitmap bitmap) {
        // increment the refcount
        AtomicInteger refCount = getRefCount(bitmap);
        int count = refCount.incrementAndGet();
        Log.d(TAG, getBitmapString(bitmap) + " + 1 = " + count);
    }

    @Override
    public void decrementRefCount(final Bitmap bitmap) {
        // decrement the refcount
        // if refcount is zero add it to candidates
        AtomicInteger refCount = getRefCount(bitmap);
        int count = refCount.decrementAndGet();
        if (count == 0) {
            synchronized (bitmapRefCounts) {
                bitmapRefCounts.remove(bitmap);
            }
            synchronized (bitmapCandidates) {
                bitmapCandidates.add(new SoftReference<>(bitmap));
            }
        }
        Log.d(TAG, getBitmapString(bitmap) + " - 1 = " + count);
    }

    @NotNull
    private AtomicInteger getRefCount(final Bitmap bitmap) {
        AtomicInteger refCount;
        synchronized (bitmapRefCounts) {
            refCount = bitmapRefCounts.get(bitmap);
            if (refCount == null) {
                refCount = new AtomicInteger(0);
                bitmapRefCounts.put(bitmap, refCount);
            }
        }
        return refCount;
    }

    /**
     * @param candidate     - Bitmap to check
     * @param targetOptions - Options that have the out* value populated
     * @return true if <code>candidate</code> can be used for inBitmap re-use with
     * <code>targetOptions</code>
     */
    private boolean canUseForInBitmap(Bitmap candidate, BitmapFactory.Options targetOptions) {
        if (candidate.isRecycled() || !candidate.isMutable()) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
            return candidate.getWidth() == targetOptions.outWidth
                   && candidate.getHeight() == targetOptions.outHeight
                   && candidate.getConfig() == targetOptions.inPreferredConfig
                   && targetOptions.inSampleSize == 1;
        }
        return canUseForInBitmapKitkatPlus(candidate, targetOptions);

    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean canUseForInBitmapKitkatPlus(final Bitmap candidate,
                                                final BitmapFactory.Options targetOptions) {
        // From Android 4.4 (KitKat) onward we can re-use if the byte size of the new bitmap
        // is smaller than the reusable bitmap candidate allocation byte count.
        int sampleSize = targetOptions.inSampleSize > 0 ? targetOptions.inSampleSize : 1;
        int width = targetOptions.outWidth / sampleSize;
        int height = targetOptions.outHeight / sampleSize;
        int byteCount = width * height * getBytesPerPixel(targetOptions.inPreferredConfig);
        return byteCount <= candidate.getAllocationByteCount();
    }

    /**
     * Return the byte usage per pixel of a bitmap based on its configuration.
     *
     * @param config The bitmap configuration.
     * @return The byte usage per pixel.
     */
    private static int getBytesPerPixel(@Nullable Bitmap.Config config) {
        if (config == null) {
            return 4;
        } else if (config == Bitmap.Config.ARGB_8888) {
            return 4;
        } else if (config == Bitmap.Config.RGB_565) {
            return 2;
        } else if (config == Bitmap.Config.ARGB_4444) {
            return 2;
        } else if (config == Bitmap.Config.ALPHA_8) {
            return 1;
        }
        return 4;
    }

    private String getBitmapString(Bitmap bitmap) {
        String name = bitmap.toString();
        name = name.substring(name.length() - 5, name.length());
        return String.format(Locale.US, "%s %4dx%4d", name, bitmap.getWidth(), bitmap.getHeight());
    }
}
