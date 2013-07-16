package com.squareup.picasso;

import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;
import java.io.File;

import static android.provider.ContactsContract.Contacts.CONTENT_URI;
import static android.provider.ContactsContract.Contacts.Photo.CONTENT_DIRECTORY;
import static com.squareup.picasso.Utils.createKey;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtils {

  static final Uri URI_1 = Uri.parse("http://example.com/1.png");
  static final Uri URI_2 = Uri.parse("http://example.com/2.png");
  static final String URI_KEY_1 = createKey(URI_1, 0, null, null);
  static final String URI_KEY_2 = createKey(URI_2, 0, null, null);
  static final Bitmap BITMAP_1 = Bitmap.createBitmap(10, 10, null);
  static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
  static final String FILE_KEY_1 = createKey(Uri.fromFile(FILE_1), 0, null, null);
  static final Uri FILE_1_URL = Uri.parse("file:///" + FILE_1.getPath());
  static final Uri FILE_1_URL_NO_AUTHORITY = Uri.parse("file:/" + FILE_1.getParent());
  static final Uri CONTENT_1_URL = Uri.parse("content://zip/zap/zoop.jpg");
  static final String CONTENT_KEY_1 = createKey(CONTENT_1_URL, 0, null, null);
  static final Uri CONTACT_URI_1 = CONTENT_URI.buildUpon().path("1234").build();
  static final String CONTACT_KEY_1 = createKey(CONTACT_URI_1, 0, null, null);
  static final Uri CONTACT_PHOTO_URI_1 =
      CONTENT_URI.buildUpon().path("1234").path(CONTENT_DIRECTORY).build();
  static final String CONTACT_PHOTO_KEY_1 = createKey(CONTACT_PHOTO_URI_1, 0, null, null);
  static final int RESOURCE_ID_1 = 1;
  static final String RESOURCE_ID_KEY_1 = createKey(null, RESOURCE_ID_1, null, null);

  static Request mockRequest(String key, Uri uri) {
    return mockRequest(key, uri, null, 0);
  }

  static Request mockRequest(String key, Uri uri, Object target) {
    return mockRequest(key, uri, target, 0);
  }

  static Request mockRequest(String key, Uri uri, Object target, int resourceId) {
    Request request = mock(Request.class);
    when(request.getKey()).thenReturn(key);
    when(request.getUri()).thenReturn(uri);
    when(request.getTarget()).thenReturn(target);
    when(request.getResourceId()).thenReturn(resourceId);
    when(request.getPicasso()).thenReturn(mock(Picasso.class));
    return request;
  }

  static Request mockCanceledRequest() {
    Request request = mock(Request.class);
    request.cancelled = true;
    when(request.isCancelled()).thenReturn(true);
    return request;
  }

  static ImageView mockImageViewTarget() {
    return mock(ImageView.class);
  }

  static Target mockTarget() {
    return mock(Target.class);
  }

  static BitmapHunter mockHunter(String key, Bitmap result, boolean skipCache) {
    BitmapHunter hunter = mock(BitmapHunter.class);
    when(hunter.getKey()).thenReturn(key);
    when(hunter.getResult()).thenReturn(result);
    when(hunter.shouldSkipCache()).thenReturn(skipCache);
    hunter.retryCount = BitmapHunter.DEFAULT_RETRY_COUNT;
    return hunter;
  }

  private TestUtils() {
  }
}
