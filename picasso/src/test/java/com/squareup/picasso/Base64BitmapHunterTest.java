package com.squareup.picasso;

import android.content.Context;
import android.net.Uri;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.squareup.picasso.Utils.createKey;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class Base64BitmapHunterTest {
  @Mock Picasso picasso;
  @Mock Cache cache;
  @Mock Stats stats;
  @Mock Dispatcher dispatcher;

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void truncatesFilePrefix() throws IOException {
    Uri uri = Uri.parse(TestUtils.DATA_URI);
    String base64 = DATA_URI.substring(22);
    Request request = new Request.Builder(uri).build();
    String key = createKey(request);

    Action action = TestUtils.mockAction(key, uri);
    AssetBitmapHunter hunter =
        spy(new Base64BitmapHunter(picasso, dispatcher, cache, stats, action));
    doReturn(null).when(hunter).decode(anyString());

    hunter.decode(request);
    verify(hunter).decodeBase64(uri);
  }
}
