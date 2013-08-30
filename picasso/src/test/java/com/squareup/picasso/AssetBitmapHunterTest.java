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
public class AssetBitmapHunterTest {
  @Mock Context context;
  @Mock Picasso picasso;
  @Mock Cache cache;
  @Mock Stats stats;
  @Mock Dispatcher dispatcher;
  @Mock Downloader downloader;

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void truncatesFilePrefix() throws IOException {
    String path = "foo/bar.png";
    Uri uri = Uri.parse("file:///android_asset/" + path);
    Request request = new Request.Builder(uri).build();
    String key = createKey(request);

    Action action = TestUtils.mockAction(key, uri);
    AssetBitmapHunter hunter =
        spy(new AssetBitmapHunter(context, picasso, dispatcher, cache, stats, action));
    doReturn(null).when(hunter).decodeAsset(anyString());

    hunter.decode(request);
    verify(hunter).decodeAsset(path);
  }
}
