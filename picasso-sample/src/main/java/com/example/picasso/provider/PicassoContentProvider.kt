package com.example.picasso.provider

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri

class PicassoContentProvider : ContentProvider() {

  override fun onCreate(): Boolean {
    autoContext = context
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?
  ) = null

  override fun getType(uri: Uri) = null

  override fun insert(
    uri: Uri,
    values: ContentValues?
  ) = null

  override fun delete(
    uri: Uri,
    selection: String?,
    selectionArgs: Array<String>?
  ) = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<String>?
  ) = 0

  companion object {
    @SuppressLint("StaticFieldLeak")
    @JvmField
    var autoContext: Context? = null
  }
}
