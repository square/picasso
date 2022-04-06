/*
 * Copyright (C) 2013 Square, Inc.
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

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.StatFs
import android.provider.Settings.Global
import android.util.Log
import androidx.core.content.ContextCompat
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.max
import kotlin.math.min

internal object Utils {
  const val THREAD_PREFIX = "Picasso-"
  const val THREAD_IDLE_NAME = THREAD_PREFIX + "Idle"
  private const val PICASSO_CACHE = "picasso-cache"
  private const val MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024 // 5MB
  private const val MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
  const val THREAD_LEAK_CLEANING_MS = 1000

  /** Thread confined to main thread for key creation.  */
  val MAIN_THREAD_KEY_BUILDER = StringBuilder()

  /** Logging  */
  const val OWNER_MAIN = "Main"
  const val OWNER_DISPATCHER = "Dispatcher"
  const val OWNER_HUNTER = "Hunter"
  const val VERB_CREATED = "created"
  const val VERB_CHANGED = "changed"
  const val VERB_IGNORED = "ignored"
  const val VERB_ENQUEUED = "enqueued"
  const val VERB_CANCELED = "canceled"
  const val VERB_RETRYING = "retrying"
  const val VERB_EXECUTING = "executing"
  const val VERB_DECODED = "decoded"
  const val VERB_TRANSFORMED = "transformed"
  const val VERB_JOINED = "joined"
  const val VERB_REMOVED = "removed"
  const val VERB_DELIVERED = "delivered"
  const val VERB_REPLAYING = "replaying"
  const val VERB_COMPLETED = "completed"
  const val VERB_ERRORED = "errored"
  const val VERB_PAUSED = "paused"
  const val VERB_RESUMED = "resumed"

  /* WebP file header
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      'R'      |      'I'      |      'F'      |      'F'      |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                           File Size                           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      'W'      |      'E'      |      'B'      |      'P'      |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  */
  private val WEBP_FILE_HEADER_RIFF: ByteString = "RIFF".encodeUtf8()
  private val WEBP_FILE_HEADER_WEBP: ByteString = "WEBP".encodeUtf8()

  fun <T> checkNotNull(value: T?, message: String?): T {
    if (value == null) {
      throw NullPointerException(message)
    }
    return value
  }

  fun checkNotMain() {
    check(!isMain) { "Method call should not happen from the main thread." }
  }

  fun checkMain() {
    check(isMain) { "Method call should happen from the main thread." }
  }

  private val isMain: Boolean
    get() = Looper.getMainLooper().thread === Thread.currentThread()

  fun getLogIdsForHunter(hunter: BitmapHunter, prefix: String = ""): String {
    return buildString {
      append(prefix)
      val action = hunter.action
      if (action != null) {
        append(action.request.logId())
      }
      val actions = hunter.actions
      if (actions != null) {
        for (i in actions.indices) {
          if (i > 0 || action != null) append(", ")
          append(actions[i].request.logId())
        }
      }
    }
  }

  fun log(owner: String, verb: String, logId: String, extras: String? = "") {
    Log.d(TAG, String.format("%1$-11s %2$-12s %3\$s %4\$s", owner, verb, logId, extras ?: ""))
  }

  fun createDefaultCacheDir(context: Context): File {
    val cache = File(context.applicationContext.cacheDir, PICASSO_CACHE)
    if (!cache.exists()) {
      cache.mkdirs()
    }
    return cache
  }

  fun calculateDiskCacheSize(dir: File): Long {
    var size = MIN_DISK_CACHE_SIZE.toLong()

    try {
      val statFs = StatFs(dir.absolutePath)
      val blockCount = statFs.blockCountLong
      val blockSize = statFs.blockSizeLong
      val available = blockCount * blockSize
      // Target 2% of the total space.
      size = available / 50
    } catch (ignored: IllegalArgumentException) {
    }

    // Bound inside min/max size for disk cache.
    return max(min(size, MAX_DISK_CACHE_SIZE.toLong()), MIN_DISK_CACHE_SIZE.toLong())
  }

  fun calculateMemoryCacheSize(context: Context): Int {
    val am = ContextCompat.getSystemService(context, ActivityManager::class.java)
    val largeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
    val memoryClass = if (largeHeap) am!!.largeMemoryClass else am!!.memoryClass
    // Target ~15% of the available heap.
    return (1024L * 1024L * memoryClass / 7).toInt()
  }

  fun isAirplaneModeOn(context: Context): Boolean {
    return try {
      val contentResolver = context.contentResolver
      Global.getInt(contentResolver, Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (e: NullPointerException) {
      // https://github.com/square/picasso/issues/761, some devices might crash here, assume that
      // airplane mode is off.
      false
    } catch (e: SecurityException) {
      // https://github.com/square/picasso/issues/1197
      false
    }
  }

  fun hasPermission(context: Context, permission: String): Boolean {
    return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
  }

  fun isWebPFile(source: BufferedSource): Boolean {
    return source.rangeEquals(0, WEBP_FILE_HEADER_RIFF) &&
      source.rangeEquals(8, WEBP_FILE_HEADER_WEBP)
  }

  fun getResourceId(resources: Resources, data: Request): Int {
    if (data.resourceId != 0 || data.uri == null) {
      return data.resourceId
    }

    val pkg = data.uri.authority ?: throw FileNotFoundException("No package provided: " + data.uri)

    val segments = data.uri.pathSegments
    return when (segments?.size ?: 0) {
      0 -> throw FileNotFoundException("No path segments: " + data.uri)
      1 -> {
        try {
          segments[0].toInt()
        } catch (e: NumberFormatException) {
          throw FileNotFoundException("Last path segment is not a resource ID: " + data.uri)
        }
      }
      2 -> {
        val type = segments[0]
        val name = segments[1]
        resources.getIdentifier(name, type, pkg)
      }
      else -> throw FileNotFoundException("More than two path segments: " + data.uri)
    }
  }

  fun getResources(
    context: Context,
    data: Request
  ): Resources {
    if (data.resourceId != 0 || data.uri == null) {
      return context.resources
    }

    return try {
      val pkg =
        data.uri.authority ?: throw FileNotFoundException("No package provided: " + data.uri)
      context.packageManager.getResourcesForApplication(pkg)
    } catch (e: NameNotFoundException) {
      throw FileNotFoundException("Unable to obtain resources for package: " + data.uri)
    }
  }

  /**
   * Prior to Android 5, HandlerThread always keeps a stack local reference to the last message
   * that was sent to it. This method makes sure that stack local reference never stays there
   * for too long by sending new messages to it every second.
   */
  fun flushStackLocalLeaks(looper: Looper) {
    val handler: Handler = object : Handler(looper) {
      override fun handleMessage(msg: Message) {
        sendMessageDelayed(obtainMessage(), THREAD_LEAK_CLEANING_MS.toLong())
      }
    }
    handler.sendMessageDelayed(handler.obtainMessage(), THREAD_LEAK_CLEANING_MS.toLong())
  }
}
