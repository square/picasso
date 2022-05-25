/*
 * Copyright (C) 2022 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.picasso

import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.GridCells.Adaptive
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale.Companion.Crop
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.compose.rememberPainter

class SampleComposeActivity : PicassoSampleActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val composeView = ComposeView(this)

    val urls = Data.URLS.toMutableList().shuffled() +
      Data.URLS.toMutableList().shuffled() +
      Data.URLS.toMutableList().shuffled()

    composeView.setContent {
      ImageGrid(urls = urls)
    }

    setContentView(composeView)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGrid(
  modifier: Modifier = Modifier,
  urls: List<String>,
  picasso: Picasso = PicassoInitializer.get()
) {
  LazyVerticalGrid(
    cells = Adaptive(150.dp),
    modifier = modifier,
  ) {
    items(urls.size) {
      val url = urls[it]
      Image(
        painter = picasso.rememberPainter(key = url) {
          it.load(url).placeholder(R.drawable.placeholder).error(R.drawable.error)
        },
        contentDescription = null,
        contentScale = Crop,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
      )
    }
  }
}
