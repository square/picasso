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
public class AssetRequestHandlerTest {
  @Mock Context context;

  @Before public void setUp() {
    initMocks(this);
  }

  @Test public void truncatesFilePrefix() throws IOException {
    String path = "foo/bar.png";
    Uri uri = Uri.parse("file:///android_asset/" + path);
    Request request = new Request.Builder(uri).build();

    AssetRequestHandler requestHandler = spy(new AssetRequestHandler(context));
    doReturn(null).when(requestHandler).decodeAsset(request, path);

    requestHandler.load(request);
    verify(requestHandler).decodeAsset(request, path);
  }
}
