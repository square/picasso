package com.example.picasso

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore.Images.Media
import android.view.View
import android.widget.ImageView
import android.widget.ViewAnimator
import com.example.picasso.provider.PicassoProvider
import com.squareup.picasso3.Callback.EmptyCallback

class SampleGalleryActivity : PicassoSampleActivity() {
  private lateinit var imageView: ImageView
  lateinit var animator: ViewAnimator

  private var image: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.sample_gallery_activity)

    animator = findViewById(R.id.animator)
    imageView = findViewById(R.id.image)

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
    // Index 1 is the progress bar. Show it while we're loading the image.
    animator.displayedChild = 1

    PicassoProvider.get()
        .load(image)
        .fit()
        .centerInside()
        .into(imageView, object : EmptyCallback() {
          override fun onSuccess() {
            // Index 0 is the image view.
            animator.displayedChild = 0
          }
        })
  }

  companion object {
    private const val GALLERY_REQUEST = 9391
    private const val KEY_IMAGE = "com.example.picasso:image"
  }
}