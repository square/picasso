/*
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 * Adapted for picasso
 */

package com.squareup.picasso;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;

final class ExifStreamReader {
  private static final String TAG = "CameraExif";

  // Returns the orientation value
  public static int getOrientation(InputStream stream) throws IOException {
    if (stream == null) {
      return 0;
    }
    MarkableInputStream markStream = new MarkableInputStream(stream);
    long mark = markStream.savePosition(65536);
    byte[] header = new byte[65536];
    markStream.read(header);
    markStream.reset(mark);
    return getOrientation(header);
  }

  // Returns the orientation value
  static int getOrientation(byte[] jpeg) {
    if (jpeg == null) {
      return 0;
    }

    int offset = 0;
    int length = 0;

    // ISO/IEC 10918-1:1993(E)
    while (offset + 3 < jpeg.length && (jpeg[offset++] & 0xFF) == 0xFF) {
      int marker = jpeg[offset] & 0xFF;

      // Check if the marker is a padding.
      if (marker == 0xFF) {
        continue;
      }
      offset++;

      // Check if the marker is SOI or TEM.
      if (marker == 0xD8 || marker == 0x01) {
        continue;
      }
      // Check if the marker is EOI or SOS.
      if (marker == 0xD9 || marker == 0xDA) {
        break;
      }

      // Get the length and check if it is reasonable.
      length = pack(jpeg, offset, 2, false);
      if (length < 2 || offset + length > jpeg.length) {
        Log.e(TAG, "Invalid length");
        return 0;
      }

      // Break if the marker is EXIF in APP1.
      if (marker == 0xE1 && length >= 8
          && pack(jpeg, offset + 2, 4, false) == 0x45786966
          && pack(jpeg, offset + 6, 2, false) == 0) {
        offset += 8;
        length -= 8;
        break;
      }

      // Skip other markers.
      offset += length;
      length = 0;
    }

    // JEITA CP-3451 Exif Version 2.2
    if (length > 8) {
      // Identify the byte order.
      int tag = pack(jpeg, offset, 4, false);
      if (tag != 0x49492A00 && tag != 0x4D4D002A) {
        Log.e(TAG, "Invalid byte order");
        return 0;
    }
    boolean littleEndian = (tag == 0x49492A00);

    // Get the offset and check if it is reasonable.
    int count = pack(jpeg, offset + 4, 4, littleEndian) + 2;
    if (count < 10 || count > length) {
      Log.e(TAG, "Invalid offset");
      return 0;
    }
    offset += count;
    length -= count;

    // Get the count and go through all the elements.
    count = pack(jpeg, offset - 2, 2, littleEndian);
    while (count-- > 0 && length >= 12) {
      // Get the tag and check if it is orientation.
      tag = pack(jpeg, offset, 2, littleEndian);
      if (tag == 0x0112) {
        int orientation = pack(jpeg, offset + 8, 2, littleEndian);
        switch (orientation) {
          case 0:
          case 1:
          case 2:
          case 3:
          case 4:
          case 5:
          case 6:
          case 7:
          case 8:
            return orientation;
          default:
            break;
        }
        Log.i(TAG, "Unsupported orientation");
        return 0;
      }
      offset += 12;
      length -= 12;
    }
   }

   Log.i(TAG, "Orientation not found");
   return 0;
  }

  private static int pack(byte[] bytes, int offset, int length, boolean littleEndian) {
    int step = 1;
    if (littleEndian) {
      offset += length - 1;
      step = -1;
    }

    int value = 0;
    while (length-- > 0) {
      value = (value << 8) | (bytes[offset] & 0xFF);
      offset += step;
    }
    return value;
  }
}

