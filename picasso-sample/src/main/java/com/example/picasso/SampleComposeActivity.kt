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

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells.Adaptive
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Request
import com.squareup.picasso3.RequestHandler
import com.squareup.picasso3.compose.rememberPainter
import kotlinx.coroutines.Dispatchers

class SampleComposeActivity : PicassoSampleActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val composeView = ComposeView(this)

    val urls = Data.URLS.toMutableList().shuffled() +
      Data.URLS.toMutableList().shuffled() +
      Data.URLS.toMutableList().shuffled()

    composeView.setContent {
      Content(urls)
    }

    setContentView(composeView)
  }
}

@Composable
fun Content(urls: List<String>, picasso: Picasso = PicassoInitializer.get()) {
  var contentScale by remember { mutableStateOf(ContentScale.Inside) }
  var alignment by remember { mutableStateOf(Alignment.Center) }

  Column {
    ImageGrid(
      modifier = Modifier.weight(1F),
      urls = urls,
      contentScale = contentScale,
      alignment = alignment,
      picasso = picasso
    )

    Options(
      modifier = Modifier
        .background(Color.DarkGray)
        .padding(vertical = 4.dp),
      onContentScaleSelected = { contentScale = it },
      onAlignmentSelected = { alignment = it }
    )
  }
}

@Composable
fun ImageGrid(
  modifier: Modifier = Modifier,
  urls: List<String>,
  contentScale: ContentScale,
  alignment: Alignment,
  picasso: Picasso = PicassoInitializer.get()
) {
  LazyVerticalGrid(
    columns = Adaptive(150.dp),
    modifier = modifier
  ) {
    items(urls.size) {
      val url = urls[it]
      Image(
        painter = picasso.rememberPainter(key = url) {
          it.load(url).placeholder(R.drawable.placeholder).error(R.drawable.error)
        },
        contentDescription = null,
        contentScale = contentScale,
        alignment = alignment,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
      )
    }
  }
}

@Composable
fun Options(
  modifier: Modifier = Modifier,
  onContentScaleSelected: (ContentScale) -> Unit,
  onAlignmentSelected: (Alignment) -> Unit
) {
  var contentScaleKey by remember { mutableStateOf("Inside") }
  var alignmentKey by remember { mutableStateOf("Center") }
  Column(modifier = modifier) {
    CONTENT_SCALES.entries.chunked(4).forEach { entries ->
      Row(
        modifier = Modifier
          .padding(2.dp)
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        entries.forEach { (key, value) ->
          OptionText(
            modifier = Modifier.weight(1F),
            key = key,
            selected = contentScaleKey == key,
            onClick = {
              contentScaleKey = key
              onContentScaleSelected(value)
            }
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    ALIGNMENTS.entries.chunked(3).forEach { entries ->
      Row(
        modifier = Modifier
          .padding(2.dp)
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        entries.forEach { (key, value) ->
          OptionText(
            modifier = Modifier.weight(1F),
            key = key,
            selected = alignmentKey == key,
            onClick = {
              alignmentKey = key
              onAlignmentSelected(value)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun OptionText(modifier: Modifier, key: String, selected: Boolean, onClick: () -> Unit) {
  Box(modifier = modifier) {
    BasicText(
      text = key,
      modifier = Modifier
        .align(Alignment.Center)
        .clip(RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .background(if (selected) Color.Blue else Color.White)
        .padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}

private val CONTENT_SCALES = mapOf(
  Pair("Crop", ContentScale.Crop),
  Pair("Fit", ContentScale.Fit),
  Pair("Inside", ContentScale.Inside),
  Pair("Fill Width", ContentScale.FillWidth),
  Pair("Fill Height", ContentScale.FillHeight),
  Pair("Fill Bounds", ContentScale.FillBounds),
  Pair("None", ContentScale.None)
)

private val ALIGNMENTS = mapOf(
  Pair("TopStart", Alignment.TopStart),
  Pair("TopCenter", Alignment.TopCenter),
  Pair("TopEnd", Alignment.TopEnd),
  Pair("CenterStart", Alignment.CenterStart),
  Pair("Center", Alignment.Center),
  Pair("CenterEnd", Alignment.CenterEnd),
  Pair("BottomStart", Alignment.BottomStart),
  Pair("BottomCenter", Alignment.BottomCenter),
  Pair("BottomEnd", Alignment.BottomEnd)
)

@Preview
@Composable
private fun ContentPreview() {
  val images = listOf(
    Color.Blue.toArgb() to IntSize(200, 100),
    Color.Red.toArgb() to IntSize(100, 200),
    Color.Green.toArgb() to IntSize(100, 100),
    Color.Yellow.toArgb() to IntSize(300, 100),
    Color.Black.toArgb() to IntSize(100, 300),
    Color.LightGray.toArgb() to IntSize(400, 100),
    Color.Cyan.toArgb() to IntSize(100, 100),
    Color.White.toArgb() to IntSize(100, 400)
  ).associateBy { (color) -> "https://cash.app/$color.png" }

  val context = LocalContext.current
  Content(
    urls = images.keys.toList(),
    picasso = remember {
      Picasso.Builder(context)
        .callFactory { throw AssertionError() } // Removes network
        .dispatchers(
          mainContext = Dispatchers.Unconfined,
          backgroundContext = Dispatchers.Unconfined
        )
        .addRequestHandler(
          object : RequestHandler() {
            override fun canHandleRequest(data: Request) = data.uri?.toString()?.run(images::containsKey) == true
            override fun load(picasso: Picasso, request: Request, callback: Callback) {
              val (color, size) = images[request.uri!!.toString()]!!
              val bitmap = Bitmap.createBitmap(size.width, size.height, Config.ARGB_8888).apply {
                Canvas(this).apply {
                  drawColor(color)
                }
              }

              callback.onSuccess(Result.Bitmap(bitmap, MEMORY))
            }
          }
        )
        .build()
    }
  )
}
