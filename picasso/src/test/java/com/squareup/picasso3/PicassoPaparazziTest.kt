package com.squareup.picasso3

import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
class PicassoPaparazziTest {

  @get:Rule val paparazzi = Paparazzi()

  @Test
  fun loadsUrlIntoImageView() {
    val bitmap = BitmapFactory.decodeStream(javaClass.classLoader.getResourceAsStream("zkaAooq.jpeg"))

    val picasso = Picasso.Builder(paparazzi.context)
      .withoutNetwork()
      .layoutLibWorkaround()
      .addRequestHandler(InMemoryRequestHandler().apply {
        addRequest("https://cash.app/picasso.png", bitmap)
      })
      .build()

    paparazzi.snapshot(ImageView(paparazzi.context).also {
      it.scaleType = ScaleType.CENTER
      picasso.load("https://cash.app/picasso.png")
        .resize(200, 200)
        .centerInside()
        .onlyScaleDown()
        .into(it)
    })
  }
}