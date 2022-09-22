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
package com.squareup.picasso3

import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER
import app.cash.paparazzi.Paparazzi
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY
import com.squareup.picasso3.RequestHandler.Result.Bitmap
import org.junit.Rule
import org.junit.Test

class PicassoPaparazziTest {

  @get:Rule val paparazzi = Paparazzi()

  @Test
  fun loadsUrlIntoImageView() {
    val picasso = Picasso.Builder(paparazzi.context)
      .callFactory { throw AssertionError() } // Removes network
      .executor(PicassoExecutorService(threadFactory = LayoutLibThreadFactory()))
      .addRequestHandler(object : RequestHandler() {
        override fun canHandleRequest(data: Request): Boolean {
          return data.uri?.lastPathSegment?.run(javaClass.classLoader::getResourceAsStream) != null
        }

        override fun load(picasso: Picasso, request: Request, callback: Callback) {
          val bitmap = BitmapFactory.decodeStream(javaClass.classLoader!!.getResourceAsStream(request.uri!!.lastPathSegment!!))
          callback.onSuccess(Bitmap(bitmap, MEMORY))
        }
      })
      .build()

    paparazzi.snapshot(
      ImageView(paparazzi.context).also {
        it.scaleType = CENTER
        picasso.load("https://cash.app/zkaAooq.jpeg")
          .resize(200, 200)
          .centerInside()
          .onlyScaleDown()
          .into(it)
      }
    )
  }
}
