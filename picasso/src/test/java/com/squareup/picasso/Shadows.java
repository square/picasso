package com.squareup.picasso;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.SparseArray;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.FileDescriptor;
import java.util.HashMap;
import java.util.Map;

import static com.squareup.picasso.TestUtils.IMAGE_THUMBNAIL_1;
import static com.squareup.picasso.TestUtils.VIDEO_THUMBNAIL_1;

public class Shadows {
  @Implements(MediaStore.Video.Thumbnails.class)
  public static class ShadowVideoThumbnails {

    @Implementation
    public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                                        BitmapFactory.Options options) {
      return VIDEO_THUMBNAIL_1;
    }
  }

  @Implements(MediaStore.Images.Thumbnails.class)
  public static class ShadowImageThumbnails {
    @Implementation
    public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                                      BitmapFactory.Options options) {
      return IMAGE_THUMBNAIL_1;
    }
  }

  @Implements(value = MediaMetadataRetriever.class)
  public static class ShadowMediaMetadataRetriever {
    private String dataSource;
    private static final Map<String, SparseArray<String>> metadata = new HashMap();
    private static final Map<String, HashMap<Long, Bitmap>> frames  = new HashMap();

    @Implementation
    public void setDataSource(String path) {
      dataSource = path;
    }

    @Implementation
    public void setDataSource(android.content.Context context, Uri uri) {
      dataSource = uri.toString();
    }

    @Implementation
    public void setDataSource(Uri uri, Map<String, String> headers) {
      dataSource = uri.toString();
    }

    @Implementation
    public void setDataSource(FileDescriptor fd) {
      dataSource = fd.toString();
    }

    @Implementation
    public void setDataSource(FileDescriptor fd, long offset, long length) {
      dataSource = fd.toString() + offset;
    }

    @Implementation
    public String extractMetadata(int keyCode) {
      if (metadata.containsKey(dataSource)) {
        return metadata.get(dataSource).get(keyCode);
      }
      return null;
    }

    @Implementation
    public Bitmap getFrameAtTime(long timeUs) {
      return frames.get(dataSource).get(timeUs);
    }

    @Implementation
    public Bitmap getFrameAtTime(long timeUs, int option) {
      return frames.get(dataSource).get(timeUs);
    }

    public static void addMetadata(String path, int keyCode, String value) {
      if (!metadata.containsKey(path)) {
        metadata.put(path, new SparseArray<String>());
      }
      metadata.get(path).put(keyCode, value);
    }

    public static void addFrame(android.content.Context context, Uri uri, long time, Bitmap bitmap) {
      String uriString = uri.toString();
      if (!frames.containsKey(uriString)) {
        frames.put(uriString, new HashMap<Long, Bitmap>());
      }
      frames.get(uriString).put(time, bitmap);
    }

    public static void reset() {
      metadata.clear();
      frames.clear();
    }
  }
}
