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
package com.example.picasso.paparazzi

import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER
import app.cash.paparazzi.Paparazzi
import com.squareup.picasso3.Picasso
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.Request
import com.squareup.picasso3.RequestHandler
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.Dispatchers

class PicassoPaparazziTest {
  @get:Rule val paparazzi = Paparazzi()

  @Test
  fun loadsUrlIntoImageView() {
    val picasso = Picasso.Builder(paparazzi.context)
      .callFactory { throw AssertionError() } // Removes network
      .dispatchers(
        mainContext = Dispatchers.Unconfined,
        backgroundContext = Dispatchers.Unconfined
      )
      .addRequestHandler(FakeRequestHandler())
      .build()

    paparazzi.snapshot(
      ImageView(paparazzi.context).apply {
        scaleType = CENTER
        picasso.load("fake:///zkaAooq.png")
          .resize(200, 200)
          .centerInside()
          .onlyScaleDown()
          .into(this)
      }
    )
  }

  class FakeRequestHandler : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
      return "fake" == data.uri!!.scheme
    }

    override fun load(picasso: Picasso, request: Request, callback: Callback) {
      val imagePath = request.uri!!.lastPathSegment!!
      callback.onSuccess(Result.Bitmap(loadBitmap(imagePath)!!, MEMORY))
    }

    private fun loadBitmap(imagePath: String): android.graphics.Bitmap? {
      val resourceAsStream = javaClass.classLoader!!.getResourceAsStream(imagePath)
      return BitmapFactory.decodeStream(resourceAsStream)
    }
  }
}
