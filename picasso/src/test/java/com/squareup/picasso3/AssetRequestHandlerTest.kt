package com.squareup.picasso3

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class AssetRequestHandlerTest {

  @Test
  @Throws(IOException::class)
  fun truncatesFilePrefix() {
    val uri = Uri.parse("file:///android_asset/foo/bar.png")
    val request = Request.Builder(uri).build()

    val actual = AssetRequestHandler.getFilePath(request)
    assertThat(actual).isEqualTo("foo/bar.png")
  }
}