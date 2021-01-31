package com.squareup.picasso3

internal object PicassoProvider {

  private val instance: Picasso by lazy {
    Picasso.Builder(checkNotNull(PicassoInitializer.appContext)).build()
  }

  @JvmStatic
  fun get() = instance
}