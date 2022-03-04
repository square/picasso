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
