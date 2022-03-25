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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore.Images.Media
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.ProgressBar
import com.example.picasso.provider.PicassoProvider
import com.squareup.picasso3.Callback.EmptyCallback

class SampleGalleryActivity : PicassoSampleActivity() {
  private lateinit var imageView: ImageView
  private lateinit var progressBar: ProgressBar

  private var image: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.sample_gallery_activity)

    imageView = findViewById(R.id.image)
    progressBar = findViewById(R.id.progress)

    findViewById<View>(R.id.go).setOnClickListener {
      val gallery = Intent(Intent.ACTION_PICK, Media.EXTERNAL_CONTENT_URI)
      startActivityForResult(gallery, GALLERY_REQUEST)
    }

    if (savedInstanceState != null) {
      image = savedInstanceState.getString(KEY_IMAGE)
      if (image != null) {
        loadImage()
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(KEY_IMAGE, image)
  }

  override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    if (requestCode == GALLERY_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
      image = data.data.toString()
      loadImage()
    } else {
      super.onActivityResult(requestCode, resultCode, data)
    }
  }

  private fun loadImage() {
    progressBar.visibility = VISIBLE
    PicassoProvider.get()
      .load(image)
      .fit()
      .centerInside()
      .into(
        imageView,
        object : EmptyCallback() {
          override fun onSuccess() {
            progressBar.visibility = GONE
          }
        }
      )
  }

  companion object {
    private const val GALLERY_REQUEST = 9391
    private const val KEY_IMAGE = "com.example.picasso:image"
  }
}
