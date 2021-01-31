package com.squareup.picasso3

import android.annotation.SuppressLint
import android.content.Context
import androidx.startup.Initializer

internal class PicassoInitializer : Initializer<Unit> {

  override fun create(context: Context) {
    appContext = context
  }

  override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

  companion object {
    @SuppressLint("StaticFieldLeak")
    @JvmField
    var appContext: Context? = null
  }
}