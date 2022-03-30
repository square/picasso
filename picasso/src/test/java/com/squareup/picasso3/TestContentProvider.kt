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

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

class TestContentProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor? = null

  override fun getType(uri: Uri): String? {
    val path = uri.path
    return when {
      path == null -> null
      path.contains("video") -> "video/"
      path.contains("images") -> "image/png"
      else -> throw IllegalArgumentException()
    }
  }

  override fun insert(
    uri: Uri,
    values: ContentValues?
  ): Uri = TODO("Not yet implemented")

  override fun delete(
    uri: Uri,
    selection: String?,
    selectionArgs: Array<out String>?
  ): Int = TODO("Not yet implemented")

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?
  ): Int = TODO("Not yet implemented")
}
